#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="${ROOT_DIR}/build"
LIB_DIR="${BUILD_DIR}/lib"
CLASSES_DIR="${BUILD_DIR}/classes"
DIST_DIR="${ROOT_DIR}/dist"
ARTIFACT_STEM="${ARTIFACT_STEM:-v34-near-transfer-java-me-auto-folders}"

if ! command -v javac >/dev/null 2>&1 || ! javac -version 2>&1 | grep -q '1\.8'; then
    echo "JDK 8 is required to emit Java 1.3-compatible class files." >&2
    exit 1
fi

rm -rf -- "${BUILD_DIR}"
mkdir -p -- "${LIB_DIR}" "${CLASSES_DIR}" "${DIST_DIR}"

mvn -q -f "${ROOT_DIR}/pom.xml" \
    dependency:copy-dependencies \
    -DoutputDirectory="${LIB_DIR}" \
    -DincludeScope=runtime

CLDC_API="${LIB_DIR}/cldcapi11-2.0.4.jar"
MIDP_API="${LIB_DIR}/midpapi20-2.0.4.jar"
JSR75_API="${LIB_DIR}/microemu-jsr-75-2.0.4.jar"
PROGUARD_CP="${LIB_DIR}/*"

if [[ ! -r "${CLDC_API}" || ! -r "${MIDP_API}" ||
        ! -r "${JSR75_API}" ]]; then
    echo "MIDP/CLDC/JSR-75 API stubs were not downloaded." >&2
    exit 1
fi

find "${ROOT_DIR}/src/main/java" -name '*.java' -print | LC_ALL=C sort \
    > "${BUILD_DIR}/sources.list"

javac \
    -source 1.3 \
    -target 1.3 \
    -Xlint:-options \
    -encoding UTF-8 \
    -bootclasspath "${CLDC_API}:${MIDP_API}:${JSR75_API}" \
    -classpath "${MIDP_API}:${JSR75_API}" \
    -d "${CLASSES_DIR}" \
    @"${BUILD_DIR}/sources.list"

RAW_JAR="${BUILD_DIR}/${ARTIFACT_STEM}-unverified.jar"
OUTPUT_JAR="${DIST_DIR}/${ARTIFACT_STEM}.jar"
OUTPUT_JAD="${DIST_DIR}/${ARTIFACT_STEM}.jad"
TEMP_JAR="${BUILD_DIR}/${ARTIFACT_STEM}-preverified.jar"
TEMP_JAD="${BUILD_DIR}/${ARTIFACT_STEM}.jad"

jar cfm "${RAW_JAR}" "${ROOT_DIR}/src/main/manifest.mf" \
    -C "${CLASSES_DIR}" .

java -cp "${PROGUARD_CP}" proguard.ProGuard \
    -injars "${RAW_JAR}" \
    -outjars "${TEMP_JAR}" \
    -libraryjars "${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}/jre/lib/rt.jar" \
    -libraryjars "${CLDC_API}" \
    -libraryjars "${MIDP_API}" \
    -libraryjars "${JSR75_API}" \
    -dontshrink \
    -dontoptimize \
    -dontobfuscate \
    -microedition \
    -target 1.1 \
    -keepattributes '*' \
    -keep 'public class com.nearbyshare.j2me.NearTransferMidlet extends javax.microedition.midlet.MIDlet { public <init>(); protected void startApp(); protected void pauseApp(); protected void destroyApp(boolean); }'

JAR_SIZE="$(stat -c '%s' "${TEMP_JAR}")"
cat > "${TEMP_JAD}" <<EOF
MIDlet-Name: Near Transfer
MIDlet-Version: 0.3.7
MIDlet-Vendor: Near Transfer
MIDlet-Description: Wi-Fi file and text transfer with NWS1.
MIDlet-1: Near Transfer,,com.nearbyshare.j2me.NearTransferMidlet
MicroEdition-Configuration: CLDC-1.1
MicroEdition-Profile: MIDP-2.0
MIDlet-Permissions-Opt: javax.microedition.io.Connector.socket, javax.microedition.io.Connector.datagram, javax.microedition.io.Connector.file.read, javax.microedition.io.Connector.file.write
MIDlet-Jar-URL: ${ARTIFACT_STEM}.jar
MIDlet-Jar-Size: ${JAR_SIZE}
EOF

if ! jar tf "${TEMP_JAR}" | grep -q \
        '^com/nearbyshare/j2me/NearTransferMidlet.class$'; then
    echo "Built JAR does not contain the MIDlet entry point." >&2
    exit 1
fi
while IFS= read -r CLASS_ENTRY; do
    if ! unzip -p "${TEMP_JAR}" "${CLASS_ENTRY}" \
            | od -An -t u1 -N8 \
            | awk '{ if ($5 != 0 || $6 != 3 || $7 != 0 || $8 != 45) exit 1 }'; then
        echo "Class is not preverified CLDC 1.1 bytecode (version 45.3): ${CLASS_ENTRY}" >&2
        exit 1
    fi
done < <(jar tf "${TEMP_JAR}" | grep '\.class$')
if ! grep -q "MIDlet-Jar-Size: ${JAR_SIZE}" "${TEMP_JAD}"; then
    echo "JAD JAR-size entry does not match the built JAR." >&2
    exit 1
fi

mv -- "${TEMP_JAR}" "${OUTPUT_JAR}"
mv -- "${TEMP_JAD}" "${OUTPUT_JAD}"
printf 'Built and preverified for CLDC 1.1 (class version 45.3):\n  %s (%s bytes)\n  %s\n' \
    "${OUTPUT_JAR}" "${JAR_SIZE}" "${OUTPUT_JAD}"