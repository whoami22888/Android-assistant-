# Third-party licences and provenance

SmolVLM2-500M-Video-Instruct Q8 model and mmproj: ggml-org, Apache-2.0, Hugging Face. Runtime downloads verified by SHA-256. https://huggingface.co/ggml-org/SmolVLM2-500M-Video-Instruct-GGUF

Native `libvisionai.so` and its llama.cpp dependencies are extracted at build time from the unmodified SceneSense v1.3 release APK: https://github.com/AlbertoEJ/SceneSense/releases/tag/v1.3 (SHA-256 37c8df6622da4081c9c863c7bb9ce7f37c0679ff47b9aa58b7ae86cda3ed485c). SceneSense source is https://github.com/AlbertoEJ/SceneSense/tree/v1.3 (AGPL-3.0); llama.cpp MIT. By distributing combined software with SceneSense's AGPL component, the combined application is made available under AGPL-3.0, with Corresponding Source under this GitHub build branch plus linked pinned upstream source. Ensure ongoing AGPL compliance if redistributing or modifying.

The machine's visible observations are suggestions subject to human review, not veterinary diagnoses. The whole user interface and save/export bridge remain open source within the public repository build branch.