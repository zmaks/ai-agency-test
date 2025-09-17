# /bin/bash

set +x

./gradlew clean bootJar
fly deploy