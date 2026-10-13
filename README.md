 <p align="center">
  <img alt="Arboretum" width="100%" src="https://castled.codes/assets/arboretum-banner.png?v=2">
</p>

<p align="center">
  <a href="https://discord.com/invite/pCKdCX6nYr"><img alt="Discord" src="https://img.shields.io/badge/Discord-Community-5865F2?style=for-the-badge&logo=discord&logoColor=white"></a>
  <a href="https://github.com/castledking/Arboretum"><img alt="GitHub Repository" src="https://img.shields.io/badge/GitHub-Repository-181717?style=for-the-badge&logo=github"></a>
  <a href="https://github.com/castledking/Arboretum/issues"><img alt="GitHub Issues" src="https://img.shields.io/badge/GitHub-Issues-181717?style=for-the-badge&logo=github"></a>
  <a href="https://castled.codes"><img alt="CASTLED CODEX" src="https://castled.codes/assets/logo-banner.png" width="140" height="35"></a>
</p>

**Arboretum** is a botanical expansion for Minecraft, bringing the diversity of real-world trees into the Overworld through distinctive bark, foliage, and carefully curated species.

From towering African baobabs to flowering jacarandas and pale-barked bald cypress, Arboretum draws inspiration from the trees that define landscapes around the world. Each species is an opportunity to discover something new about the natural world.

The goal is not simply to add more trees. It is to build a growing, scientifically grounded collection of woody plants that makes Minecraft's landscapes more diverse, recognizable, and rewarding to explore.

---

## The idea

Minecraft has a recognizable collection of tree types, but the diversity of real-world trees is far greater than a handful of silhouettes and wood palettes.

Arboretum explores that gap by reimagining familiar Minecraft tree assets as distinct botanical species, with room to expand into a much larger catalogue.

- **Botanical inspiration** — Species based on real trees, with distinctive bark, foliage, colors, and growth characteristics.
- **Scientific identity** — Common names, scientific names, plant families, habitats, and other botanical details.
- **Exploration** — Discover the visual diversity of trees inspired by different regions and ecosystems.
- **Curated collections** — Select a small set of species, a broader collection, or build a custom selection.
- **Shared content** — A common species catalogue designed to support different Minecraft implementations.

---

## Species

Each tree starts with a real-world reference. Its visual identity should reflect what makes that species recognizable, rather than simply applying a different color to an existing tree.

| Species | Inspiration | Visual identity |
|---|---|---|
| Baobab (*Adansonia*) | African savannas | Massive trunks and broad, irregular canopies |
| Jacaranda (*Jacaranda mimosifolia*) | South American flowering trees | Dark wood and purple-blue blossoms |
| Bald cypress (*Taxodium distichum*) | North American wetlands | Pale, textured bark and distinctive roots |
| More species | Worldwide | A growing botanical catalogue |

The initial species are a starting point, not a limit. The catalogue is intended to grow as additional trees are researched, designed, and implemented.

### Botanical information

Species data is intended to go beyond appearance.

A botanical entry may include:

- Common name and scientific binomial
- Plant family
- Native range and habitat
- Distinguishing characteristics
- Interesting facts and reference sources

This information can be presented through item lore or other in-game interfaces where the implementation supports it.

Scientific names and biological details should be grounded in reliable botanical references, keeping the educational side of the project accurate as the catalogue expands.

---

## Collections

Not every world needs every species. Arboretum is designed around selectable collections, allowing players and server owners to decide how much variety they want.

| Preset | Purpose |
|---|---|
| **Small** | A lightweight selection of distinctive species |
| **Medium** | A broader collection for greater visual diversity |
| **Complete** | Every available species in the catalogue |
| **Custom** | Individually selected species and collections |

Presets are intended to be curated configurations of the same underlying species catalogue, rather than separate implementations of the same content.

This keeps collection definitions manageable as the project grows and makes custom combinations possible without maintaining a separate pack for every selection.

*Collection presets and custom selection are planned features.*

---

## Platform support

Arboretum is intended to support both standalone visual changes and richer implementations that can provide custom blocks and additional in-game functionality.

### Standalone resource pack

A resource pack provides the most accessible entry point. Players can use supported visual replacements without installing a mod or running a specialized server.

This path prioritizes textures, models, and other client-side assets.

### Client mod

A mod can provide the foundation for features that go beyond visual replacement, including species metadata, configuration, custom content, and in-game botanical information.

The exact capabilities and supported mod loaders will be documented as the implementation develops.

### Paper servers

Paper support is planned around a plugin-backed implementation using CraftEngine.

CraftEngine can provide custom block presentation and resource-pack integration, while a companion plugin can manage species data and server-side behavior. This also provides a path toward more specialized block shapes, such as stairs, roots, and other botanical details, subject to available collision and state support.

The intention is to make Arboretum's content usable on Paper servers without requiring players to install a full client mod.

### Resource-pack distribution

RSPM integration is planned as an additional distribution option, alongside a directly downloadable standalone resource pack.

Distribution tooling should remain separate from the species catalogue so the same content can be packaged for different environments.

---

## Design principles

### One catalogue, multiple implementations

Species definitions should remain independent of the platform that renders them.

```text
                    Arboretum
                        |
                Species catalogue
                        |
          +-------------+-------------+
          |             |             |
          v             v             v
     Resource pack   Client mod   Paper plugin
          |             |             |
       Textures      Mod features   CraftEngine
       and models    and metadata   and pack assets
```

The intended separation keeps botanical information, species selection, and asset organization reusable across implementations.

Platform-specific behavior belongs in the relevant implementation, rather than being duplicated throughout the catalogue.

### Visual identity matters

A species should be recognizable through its combination of bark, foliage, color, and overall visual character.

The aim is to preserve Minecraft's visual language while drawing on the botanical differences that make real trees interesting.

### Scientific accuracy over arbitrary variety

Species selection and visual design should be informed by real-world references. Where Minecraft's block-based limitations require artistic interpretation, the result should remain recognizable and the distinction should be understood as an adaptation.

---

## Development status

Arboretum is a growing project. Initial visual concepts establish the direction for baobab, jacaranda, and bald cypress-inspired designs.

The broader catalogue, collection presets, scientific lore, client-mod implementation, and Paper/CraftEngine integration are planned development areas, not claims of completed functionality.

As each part is implemented, its compatibility, requirements, and limitations should be documented explicitly.

---
