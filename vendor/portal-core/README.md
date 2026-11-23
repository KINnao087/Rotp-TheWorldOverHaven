# TWOH rift engine (Forge 1.16.5)

This is an internal **library**, not a second Forge mod. The normal build consumes the checked-in twoh-rift-engine-1.1.jar. It does not resolve the upstream mod from Maven or require it on the client/server. Gradle remaps the SRG library for compilation and folds its classes into the release. The two tiny Embeddium interface declarations are compile-only; the running client's real Embeddium supplies them.

The binary portion is derived from the public 0.17 Forge release (file 3562072), SHA-256 `54d5d70997413bc63f8ad61938595c803d8efccdde1101afeb9e1484babfd189`. To regenerate it and its mixin configs/refmap and render resources, run `python vendor/portal-core/extract.py <path-to-original-release>` from the project. The script relocates the runtime, channels and resources. It excludes the upstream mod entry point, metadata, peripheral package, worldgen data, launcher, commands registration, and portal generation mixins. It is not a second copy of the complete mod. Upstream Java and changes are identified in the third-party notice packaged in the release.

The Forge lifecycle belongs to TWOH. Embeddium integration lives in `EmbeddiumRiftChunks`; each world owns its own listener. Do not notify the globally active renderer for a chunk received for another dimension. Keep the ordinary chunk provider path for installations without Embeddium.

Do not co-install the standalone Immersive Portals mod in this version. Both implementations hook vanilla world switching. Its separately registered dimensions, blocks and portals are not imported by TWOH. Back up an old world before migrating.

