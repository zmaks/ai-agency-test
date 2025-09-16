# System overview (PoC)

**Goal:** turn a natural-language request into a runnable YouTrack “agent” (workflow) using a tiny, LLM-friendly JSON DSL and a blackboard-style runtime.

**Main components (short):**

* **Catalog** – small, generic node types the planner can compose (trigger, adapter op, transform, LLM).
* **Capability manifest** – app-specific operations (YouTrack) with args & result schemas.
* **Planner (LLM)** – given user prompt + catalogs, emits a valid **Agent JSON** (no prose).
* **Runner** – executes the Agent JSON:

    * Keeps a shared **ctx** (event, vars, nodes outputs).
    * Resolves `bindings` (references into ctx), respects `when`, writes outputs back to ctx.
    * Calls capability ops, LLM, JS transforms.
* **Visualizer** – infers edges from `bindings`/`when` and renders a DAG (we also show ASCII).

---

# Nodes catalog (generic, minimal)

Each node instance:

* `id`: unique
* `type`: from the catalog below
* `params`: node config
* `bindings`: values pulled from ctx via `{ "ref": "#nodeId.field" }` or `{ "ref": "$.path" }`
* `when`: optional condition `{ "ref": "#nodeId.boolField" }` (skip node if falsy)
* `writes`: optional `"field" -> "$.path"` extra copies beyond `ctx.nodes[id]`

**Value refs**:

* `#n.field` → `ctx.nodes.n.field`
* `$.something` → any path in `ctx` (e.g., `$.event.issueId`, `$.vars.*`)

---

## Catalog (with short descriptions)

```json
[
  {
    "kind": "trigger",
    "type": "trigger.event",
    "title": "Event Trigger",
    "description": "Seeds ctx.event from an external webhook-like event.",
    "inputs": {},
    "outputs": { "event": "object" }
  },
  {
    "kind": "action",
    "type": "adapter.operation",
    "title": "Adapter Operation",
    "description": "Invokes an operation from a capability manifest (e.g., YouTrack.AddComment).",
    "inputs": { "args": "object" },
    "outputs": { "result": "object" },
    "params": { "capabilityId": "string", "operation": "string" }
  },
  {
    "kind": "logic",
    "type": "transform.js",
    "title": "JS Transform",
    "description": "Runs small JS to reshape/compute values (safe sandbox, time-limited).",
    "inputs": { "data": "object" },
    "outputs": { "result": "object" },
    "params": { "code": "string" }
  },
  {
    "kind": "logic",
    "type": "transform.template",
    "title": "Template",
    "description": "Renders a string from input object using a simple {{mustache}}-style template.",
    "inputs": { "data": "object" },
    "outputs": { "text": "string" },
    "params": { "template": "string" }
  },
  {
    "kind": "llm",
    "type": "llm.extract",
    "title": "LLM Extract (Multi-Input)",
    "description": "Given an array of inputs (texts or files), extracts a structured object per jsonSchema. Handles PDFs internally (PDF→text) before extraction.",
    "inputs": {
      "items": "array" 
      /* items: 
         { kind: "text", text: "..." } |
         { kind: "file", mimeType: "application/pdf|...","blobRef":"blob://...","base64":"... (optional)" }
      */
    },
    "outputs": { "object": "object" },
    "params": {
      "jsonSchema": "object",
      "instructions": "string",
      "pdfTextOptions": "object"
    }
  }
]
```

**Notes**

* We *merged* “PDF→Text” into `llm.extract`. For PoC: if `mimeType` is PDF, runner extracts text (simple lib) and feeds that text to the LLM along with any plain text items.
* `items` can mix texts and files; runner prepares a single textual corpus (or batched) for the LLM under the hood.

---

# YouTrack capability manifest (operations catalog)

Short and sufficient for the example. Your adapter enforces `argsSchema` on call and shapes `result` to `resultSchema`.

```json
{
  "capabilityId": "youtrack",
  "baseUrl": "https://youtrack.example.com/api",
  "auth": "bearer",
  "operations": [
    {
      "name": "GetAttachment",
      "method": "GET",
      "path": "/issues/{issueId}/attachments/{attachmentId}",
      "argsSchema": {
        "type": "object",
        "properties": {
          "issueId": { "type": "string" },
          "attachmentId": { "type": "string" }
        },
        "required": ["issueId","attachmentId"]
      },
      "resultSchema": {
        "type": "object",
        "properties": {
          "blobRef": { "type": "string" },
          "mimeType": { "type": "string" },
          "filename": { "type": "string" }
        },
        "required": ["blobRef","mimeType"]
      },
      "mapping": { "download": true, "blobField": "blobRef", "mimeField": "mimeType", "nameField": "filename" }
    },
    {
      "name": "AddComment",
      "method": "POST",
      "path": "/issues/{issueId}/comments",
      "argsSchema": {
        "type": "object",
        "properties": {
          "issueId": { "type": "string" },
          "text": { "type": "string" }
        },
        "required": ["issueId","text"]
      },
      "resultSchema": {
        "type": "object",
        "properties": { "commentId": { "type": "string" } }
      },
      "mapping": { "resultPath": "$" }
    }
  ]
}
```

---

# Example planner input (prompt to the planner LLM)

**System to planner:**
“Output **only** a valid Agent JSON per the provided catalog. Use `trigger.event`, `adapter.operation`, `transform.js`, `transform.template`, and `llm.extract`. Bind inputs with `{ 'ref': '#node.field' }` or `{ 'ref': '$.path' }`. Use `when` for gating. Avoid prose.”

**User task:**
“I want an agent that, when an attachment is added to a YouTrack issue, checks the file for YouTrack issue mentions and comments them on the issue. Only post a comment if at least one mention is found.”

**Context provided to planner:**

* Nodes catalog (above)
* YouTrack capability manifest (above)
* Event shape example:

  ```json
  { "eventName": "youtrack.attachment.added", "issueId": "ABC-1", "attachmentId": "att-123" }
  ```

---

# Planner output (AI agent workflow as JSON)

Below includes short **descriptions** for each instance in `params.note` (purely for human/UI clarity; runner can ignore).

```json
{
  "name": "PDF Mentions → Comment (Context Model)",
  "nodes": [
    {
      "id": "t1",
      "type": "trigger.event",
      "params": {
        "note": "Webhook entry point. Copies external payload into ctx.event."
      },
      "writes": { "event": "$.event" }
    },
    {
      "id": "pick_ids",
      "type": "transform.js",
      "params": {
        "note": "Extract issueId and attachmentId from event to vars.",
        "code": "const e=inputs.data||{}; return { issueId: e.issueId, attachmentId: e.attachmentId };"
      },
      "bindings": { "data": { "ref": "$.event" } },
      "writes": { "issueId": "$.vars.issueId", "attachmentId": "$.vars.attachmentId" }
    },
    {
      "id": "get_attachment",
      "type": "adapter.operation",
      "params": {
        "note": "Download the attachment and get mimeType/filename.",
        "capabilityId": "youtrack",
        "operation": "GetAttachment"
      },
      "bindings": {
        "args.issueId": { "ref": "$.vars.issueId" },
        "args.attachmentId": { "ref": "$.vars.attachmentId" }
      }
    },
    {
      "id": "mentions_extract",
      "type": "llm.extract",
      "params": {
        "note": "Single node that handles PDFs internally, then extracts issue keys via JSON schema.",
        "instructions": "Extract YouTrack issue keys like ABC-123 from the provided content. Return strict JSON.",
        "jsonSchema": {
          "type": "object",
          "properties": { "mentions": { "type": "array", "items": { "type": "string" } } },
          "required": ["mentions"]
        },
        "pdfTextOptions": { "mode": "fast" }
      },
      "bindings": {
        "items": {
          "ref": "#get_attachment.result"
        }
        /* runner transforms:
           if bindings.items is an object (not an array), wrap into:
           [{kind:'file', mimeType: result.mimeType, blobRef: result.blobRef, filename: result.filename}]
        */
      }
    },
    {
      "id": "has_mentions",
      "type": "transform.js",
      "params": {
        "note": "Normalize & test if any mentions were found.",
        "code": "const m=(inputs.data?.mentions||[]).map(x=>String(x).toUpperCase()); const uniq=[...new Set(m)]; return { ok: uniq.length>0, mentions: uniq };"
      },
      "bindings": { "data": { "ref": "#mentions_extract.object" } }
    },
    {
      "id": "comment_text",
      "type": "transform.template",
      "when": { "ref": "#has_mentions.ok" },
      "params": {
        "note": "Format the comment body.",
        "template": "Found issue mentions in attachment: {{mentions.join(', ')}}"
      },
      "bindings": { "data": { "ref": "#has_mentions" } }
    },
    {
      "id": "add_comment",
      "type": "adapter.operation",
      "when": { "ref": "#has_mentions.ok" },
      "params": {
        "note": "Post the comment to YouTrack.",
        "capabilityId": "youtrack",
        "operation": "AddComment"
      },
      "bindings": {
        "args.issueId": { "ref": "$.vars.issueId" },
        "args.text": { "ref": "#comment_text.text" }
      }
    }
  ]
}
```

---

# ASCII schema (clean)

```
                          ┌───────────────┐
                          │   [t1]        │
                          │ Event Trigger │
                          └───────┬───────┘
                                  │
                                  ▼
                          ┌───────────────┐
                          │  [pick_ids]   │
                          │ JS: get IDs   │
                          └───────┬───────┘
                                  │
                                  ▼
                   ┌──────────────┴──────────────┐
                   │      [get_attachment]       │
                   │  YouTrack: GetAttachment    │
                   └──────────────┬──────────────┘
                                  │  file + mime
                                  ▼
                          ┌───────────────────┐
                          │ [mentions_extract]│
                          │  LLM Extract (PDF │
                          │  handled inside)  │
                          └───────┬───────────┘
                                  │ mentions
                                  ▼
                          ┌─────────────────┐
                          │  [has_mentions] │
                          │ JS: any & dedupe│
                          └───────┬─────────┘
                                  │ ok?
                                  ▼
                       ┌────────────────────────┐
                       │    ◇ if mentions > 0  │
                       └──────────┬───────┬────┘
                                  │Yes    │No
                                  │       │
                                  │       └────────► [END: skip]
                                  │
                                  ▼
                          ┌───────────────┐
                          │ [comment_text]│
                          │   Template    │
                          └───────┬───────┘
                                  │
                                  ▼
                        ┌───────────────────────┐
                        │     [add_comment]     │
                        │ YouTrack: AddComment  │
                        └───────────────────────┘
```

---

## One-line descriptions of **instances** (from the example)

* **t1 / Event Trigger:** Copies the incoming webhook payload to `ctx.event`.
* **pick\_ids / JS Transform:** Pulls `issueId` and `attachmentId` from the event into `ctx.vars`.
* **get\_attachment / Adapter Operation:** Downloads the attachment; outputs `{blobRef,mimeType,filename}`.
* **mentions\_extract / LLM Extract (Multi-Input):** Receives the file; if PDF → extracts text internally, then finds YouTrack issue keys per `jsonSchema`.
* **has\_mentions / JS Transform:** Normalizes to uppercase, dedupes, sets `ok` flag.
* **comment\_text / Template:** Builds the comment string from `mentions`.
* **add\_comment / Adapter Operation:** Posts the comment to the issue (only if `ok` is true).

---

### Implementation tip (runner)

For `llm.extract`:

* Normalize `bindings.items` to an array of:

    * `{kind:'text', text:'...'}`
    * `{kind:'file', mimeType:'...', blobRef:'blob://..', base64?:'...' }`
* For each item:

    * If `kind:file` and `mimeType` is PDF → run a quick PDF-to-text extraction (PoC-friendly lib), append resulting text to a corpus list.
    * If `kind:text` → append text directly.
* Concatenate text with separators (filename headers), then call LLM with `instructions` + `jsonSchema`.
* Set `ctx.nodes[id] = { object: <parsed-json> }`.

That’s it — compact, generic, and ready to ship as a PoC.
