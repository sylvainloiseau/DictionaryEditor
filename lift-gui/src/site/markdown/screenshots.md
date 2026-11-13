# Screenshots of the documentation

The images of `src/site/resources/images` are made by a program, not by hand:

```sh
mvn -Pscreenshots verify -DskipUnitTests
```

What happens:

1. **Compiling.** `DocumentationScreenshotsIT` is a test source (`src/test/java/fr/cnrs/lacito/liftgui/doc`). Its name ends in `IT`, so the unit tests (surefire) leave it out.
2. **Taking the screenshots.** In the `integration-test` phase, the `screenshots` profile runs `DocumentationScreenshotsIT` with maven-failsafe. It is a TestFX program:
   - it starts the application headless (Monocle), in English, with fresh preferences;
   - it opens `src/main/resources/lift/20260302.lift`;
   - for each image, it shows a view, selects a row, and saves the window (1400 × 900, rendered at 2×) in `target/screenshots`.
3. **Copying.** In the `post-integration-test` phase, the images are copied to `src/site/resources/images`, replacing the old ones.

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
