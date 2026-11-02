# Screenshots of the documentation

The images of `Doc/img` are made by a program, not by hand:

```sh
mvn -Pscreenshots verify -DskipUnitTests
```

What happens:

1. **Compiling.** The `screenshots` profile of the `pom.xml` adds `Doc/screenshots/java` to the test sources.
2. **Taking the screenshots.** In the `integration-test` phase, maven-failsafe runs `DocumentationScreenshotsIT`, a TestFX program:
   - it starts the application headless (Monocle), in English, with fresh preferences;
   - it opens `src/main/resources/lift/20260302.lift`;
   - for each image, it shows a view, selects a row, and saves the window (1400 × 900, rendered at 2×) in `target/screenshots`.
3. **Copying.** In the `post-integration-test` phase, the images are copied to `Doc/img`, replacing the old ones.

Leave out `-DskipUnitTests` to run the unit tests too.

| Image | View | Row selected |
|---|---|---|
| `entries.png` | Objects ▸ Entries | 1st |
| `Sense.png` | Objects ▸ Senses | 1st |
| `MetaLanguages.png` | Languages ▸ Meta-languages | 1st |
| `GramInfo.png` | Dictionary configuration ▸ Ranges ▸ grammatical-info | 2nd |
| `BrowseNoteType.png` | Category browser ▸ Note types | 1st |

**Options** (`-D…`):

- `screenshots.scale=1`: images at 1×; the default, 2, suits high-density screens;
- `headless=false -Dglass.platform=`: watch the window while the images are taken.

**Adding an image:** in `DocumentationScreenshotsIT`, write a `@Test` method that shows the view and calls `save("Name.png")`. It reuses the helpers of the UI tests (`MainWindowTest`: `openView`, `selectRow`…).
