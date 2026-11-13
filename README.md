# Dictionary editor for descriptive linguistics

<p align="center">
  <img src="https://github.com/sylvainloiseau/DictionaryEditor/blob/main/lift-gui/src/main/resources/fr/cnrs/lacito/liftgui/ui/icons/app/icon-256.png?raw=true" />
</p>

Application for editing dictionaries compatible with the [Lexicon Interchange FormaT - LIFT](https://github.com/sillsdev/lift-standard) data format (format is used by [SIL Fieldworks](https://software.sil.org/fr/fieldworks/) and [ELAN](https://archive.mpi.nl/tla/elan)).

## Installation

Pick an installer for your plateform from the [github release page](https://github.com/sylvainloiseau/DictionaryEditor/releases).

## Feature

- DictionaryEditor profide a complete access to all the components of the LIFT data model: entries, sense, example, translation, illustration, trait, note, field, etc.
- List each component type in table -- helping to increase comprehensiveness and consistency of the lexicographic description: missing field in a given language or field is more easy to spot
- choose the columns of each table (Columns button, or Preferences ▸ Tables): any property of the component, its fields, traits, notes and annotations, the number of its subcomponents, the properties of its parent and ancestors; multilingual texts get one column per language, and can be edited in the table
- filter each column from its header: text (match case, regular expression, empty / not empty), values to tick with their counts, or a range with a histogram for numbers, counts and dates
- edit objects in forms 
- browse and manage categories (field, note, trait, annotation, grammatical category, feature set)
- It exports the dictionary as a LaTeX document, and as a PDF when XeLaTeX or LuaLaTeX is installed (Tools ▸ Export as LaTeX / PDF): the polyglossia language and the font of each language, a thematic part by semantic domain, the illustrations as figures, and a quick preview on a sample. The settings can be saved as a JSON file, which `lift-api latex --config` also reads. A custom XSLT stylesheet can replace parts of the bundled ones (the LaTeX macros, the abbreviations…); the dialog creates a starting one.

### Listing entries

![Listing entity](lift-gui/src/site/resources/images/entries.png)

### Listing Senses

![Listing sense](lift-gui/src/site/resources/images/Sense.png)

### Listing all metalanguage forms

![Listing metalanguage](lift-gui/src/site/resources/images/MetaLanguages.png)

### Browse note type

![Browse note type](lift-gui/src/site/resources/images/BrowseNoteType.png)

### Managing part of speech

![Managing part of speech](lift-gui/src/site/resources/images/GramInfo.png)

## Installation from sources

```bash
git clone "https://github.com/sylvainloiseau/DictionaryEditor.git"
cd DictionaryEditor
mvn install -DskipTests
```

Run (after `mvn install`):

```bash
mvn -f lift-gui javafx:run
```

Tests :

```bash
mvn test
```

Documentation (the Markdown pages of `lift-gui/src/site/markdown`, built into `lift-gui/target/site`):

```bash
mvn site
```

### Create a standalone app for all platforms (Windows, Linux, macOS)

The packaged standalone app will include a lighweight java runtime.

#### Dependencies

- JDK 21
- Maven 3.9+

#### Command line

On the repository root:

```bash
mvn clean install -Pdist -DskipTests \
  -Djavafx.platform=<plateforme> \
  -Djpackage.type=<type>
```

- Set the option `-Djavafx.platform` to the system on which you compile :

| target OS (build)   | Valeur de `-Djavafx.platform` |
|---------------------|-------------------------------|
| Linux x86_64        | `linux`                       |
| Windows x86_64      | `win`                         |
| macOS Apple Silicon | `mac-aarch64`                 |
| macOS Intel         | `mac`                         |

See the next sections for the values for `jpackage.type`.

##### Windows

**1/ With WiX installer**

**[WiX Toolset](https://wixtoolset.org/)** should be installed and in the `PATH`.

Files are in `lift-gui/target/jpackage/` (or `jpackage-msi` / `jpackage-exe` if you use suffixes as in the CI).

```bash
mvn clean install -Pdist -DskipTests \
  -Djavafx.platform=win \
  -Djpackage.type=MSI
```

**2/ Without WiX — app image** : the POM activates Windows options (`--win-menu`) incompatible with `app-image`. After a build that produced the jlink runtime (`lift-gui/target/maven-jlink/default/`), run **jpackage** once manually.

```bash
"%JAVA_HOME%\bin\jpackage.exe" ^
  --name DictionaryEditor ^
  --dest lift-gui\target\jpackage-app ^
  --type app-image ^
  --app-version 1.0.0 ^
  --runtime-image lift-gui\target\maven-jlink\default ^
  --vendor "CNRS LACITO" ^
  --module fr.cnrs.lacito.liftgui/fr.cnrs.lacito.liftgui.MainApp
```

Then run **`lift-gui\target\jpackage-app\DictionaryEditor\DictionaryEditor.exe`**.

If `mvn install … -Pdist` fails only on **jpackage** (WiX missing) but the **`lift-gui/target/maven-jlink/default`** folder has been created, the `jpackage` command above is sufficient to obtain the portable image. Otherwise, install **WiX** and use `-Djpackage.type=MSI` or `EXE`.

### Linux

Create deb package (default `jpackage.type` value)

```bash
mvn clean install -Pdist -DskipTests \
  -Djavafx.platform=linux \
  -Djpackage.type=DEB
```

On Debian/Ubuntu, `fakeroot` tools may be needed; the CI also installs `rpm` for some steps. The `.deb` is generated under `lift-gui/target/jpackage/`. Installation :

```bash
sudo dpkg -i lift-gui/target/jpackage/*.deb
```

Then run the application from the applications menu or the command installed by the package.

### macOS

Create a disk imaege (`.dmg`) (select the correct value for `javafx.platform` : `mac-aarch64` or `mac`, see above) :

```bash
mvn clean install -Pdist -DskipTests \
  -Djavafx.platform=mac-aarch64 \
  -Djpackage.type=DMG
```

On Mac M1, try `-Djavafx.platform=mac` if your JDK/OpenJFX provides it. The `.dmg` is generated under `lift-gui/target/jpackage/`. Open the DMG and drag the application into **Applications**, then run it from the Launchpad or the Applications folder.

### Releases GitHub

A GitHub workflow (`.github/workflows/release.yml`) automatically builds the installers for **Linux**, **Windows** and **macOS** when a **tag** is pushed, for example :

```bash
git tag v1.0.0
git push origin v1.0.0
```

The artifacts are attached to the corresponding **Release** on GitHub (`.deb`, `.msi`, `.exe`, `.dmg` files according to the successful jobs).

The same tag publishes the `lift-fx` library on Maven Central, with the version of the tag (see below).

## The lift-fx library

The project has two Maven modules: `lift-gui`, the application, and `lift-fx`, the JavaFX editors of the LIFT components, which any JavaFX application can use:

```xml
<dependency>
  <groupId>io.github.sylvainloiseau</groupId>
  <artifactId>lift-fx</artifactId>
  <version>1.0.0</version>
</dependency>
```

```java
// module-info.java: requires fr.cnrs.lacito.liftfx; (brings lift-api and javafx.controls)
LiftComponentEditor editor = new LiftComponentEditor(dictionary); // a lift-api LiftDictionary
editor.setComponent(entry);                                       // an entry, a sense, an example, a note…
pane.getChildren().add(editor);
```

The dictionary gives the languages offered (object languages, metalanguages), the types and the ranges. `LiftFx.setLocale` sets the language of the editors (English or French). `lift-fx` needs Java 25, like `lift-api`.

To publish it by hand: `mvn -P release -pl lift-fx -am deploy` (the credentials of the server `central` in `settings.xml`, and a GPG key). `-DskipUpload -Dgpg.skip` builds the bundle in `target/central-publishing` without uploading it.

## Icon

The application icon is plate 16 of:

> Le Vaillant, François. *Histoire naturelle des oiseaux de paradis et des rolliers, suivie de celle des toucans et des barbus*. Vol. 1. Paris: Denné le jeune, Perlet, 1806. Plate 16: « Le Nébuleux, étalant ses parures » (the "Nébuleux" bird-of-paradise displaying its plumes), painted by Jacques Barraband, engraved by Pérée, printed by Langlois. DOI: [10.5962/bhl.title.61652](https://doi.org/10.5962/bhl.title.61652).

The scan is from the Smithsonian Libraries copy, digitised by the [Biodiversity Heritage Library](https://www.biodiversitylibrary.org/page/40634990). The file used is [Histoire naturelle des oiseaux de paradis et des rolliers (No. 16) (8112364012).jpg](https://commons.wikimedia.org/wiki/File:Histoire_naturelle_des_oiseaux_de_paradis_et_des_rolliers_(No._16)_(8112364012).jpg) on Wikimedia Commons. The illustration is in the public domain. For the icon, the plate was cropped to the bird, its paper whitened (the uneven lighting evened out, a fold line removed), and white margins added to make it square.

## Authors

- Computer science students, Sorbonne Paris Nord University:
  - Inès GBADAMASSI
  - Maryse GOEH-AKUE
  - Ermeline BRESSON
  - Ayman JARI
  - Erij MAZOUZ
- Sylvain Loiseau, Sorbonne Paris Nord University / Lacito-CNRS laboratory

## Licence

Licensed under the [Academic Free License version 3.0](https://opensource.org/license/AFL-3.0) (see [LICENSE](LICENSE)), like [lift-api](https://github.com/sylvainloiseau/lift-api). This covers the application (`lift-gui`) and the `lift-fx` library.
