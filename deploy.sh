# /bin/bash

set +x

./gradlew bootJar
fly deploy