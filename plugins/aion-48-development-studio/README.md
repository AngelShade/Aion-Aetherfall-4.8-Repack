# Aion 4.8 Development Studio

Private project plugin for Aion Aetherfall. Version 0.1.0 bundles four focused skills:

- **Aion development:** bounded diagnosis, source repair, normal builds and delivery.
- **Native client:** English UI, PAK/XML/Lua, native integrations and recovery cohorts.
- **Playerbots:** Aion contracts, native persistence, movement, combat and port scope.
- **Repack:** explicit original-to-repack synchronization, changelogs and cumulative patches.

The maintained source is this directory. The repo marketplace is
`.agents/plugins/marketplace.json`, named `aion-aetherfall`. Refresh/reinstall the local
plugin after editing its source, then use a new chat to load the updated skills.
Installation status must be confirmed by the app/CLI; the folder alone is not an install.

The plugin loads current project notes and existing tools when relevant. It does not
embed frozen release versions, duplicate the project history, run background watchers,
or add an MCP service. It grants no new lifecycle, SQL or publishing permissions.
The original development installation is the default destination. Repack updates and
publication require explicit instructions. No credentials or client/game data are bundled.

Package layout follows the official OpenAI plugin guidance:
https://developers.openai.com/plugins/build/plugins
