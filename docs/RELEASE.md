# Release optimization

The release variant enables R8 full mode, bytecode shrinking, obfuscation, and optimized resource shrinking.
The debug variant retains its existing configuration.

## Build

```sh
./gradlew :app:testDebugUnitTest :app:assembleRelease
```

Use the existing release credentials through `keystore.properties` or the environment variables documented in `keystore.properties.example`.
The optimization change leaves signing, application ID, version name, version code, and ABI splits under the existing release process.

## Native code and logs

The Root TUN C++ library uses `-O3`, Thin LTO, hidden symbol visibility, section collection, and safe identical-code folding.
These flags apply to `Release` and `RelWithDebInfo`, which AGP uses for the release variant.
AGP strips native debug symbols from APKs and retains separate native symbol files.
The bundled Rust network library stays unchanged.

Release builds remove Android verbose, debug, and info log calls while retaining warnings and errors.
Root TUN native debug and info logs are disabled when `NDEBUG` is set.

## Runtime entry points

The keep rules preserve JNI class and method names and the Root service entry points used by libsu's separate loader.
Other code remains eligible for shrinking, obfuscation, and optimization.
Dependency keep rules remain enabled.

Keep the matching `app/build/outputs/mapping/release/mapping.txt` to decode obfuscated stack traces.
After changing optimization settings, test Root service startup and the VPN transport on a device.
