# Vendored: sherpa-onnx Java API

These files are copied verbatim from [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx)
by Xiaomi Corporation and contributors, tag **v1.13.4**, from
`sherpa-onnx/java-api/src/main/java/com/k2fsa/sherpa/onnx/`.

Licensed under the Apache License 2.0, the same as the rest of sherpa-onnx. The
copyright headers are left as they were found.

## Why they are copied rather than depended on

The project publishes no Java artefact to Maven Central, so there is nothing to
declare as a dependency. Only the classes reachable from `OnlineRecognizer` are
here, which is the streaming recogniser and the configuration objects it needs;
the offline recogniser, text to speech, speaker identification and the rest of
the library are not.

## The package name is not ours to change

They stay in `com.k2fsa.sherpa.onnx` on purpose. The native library looks its
Java classes up by their fully qualified names, so moving them into our own
package would break every call across the JNI boundary.

## Updating

The Java classes and the native library are one unit: `SherpaNatives.VERSION`
must match the tag these files came from, and the pinned SHA-256 hashes must be
the ones from that release. Changing one without the others will fail at load
time, which is the intended outcome.
