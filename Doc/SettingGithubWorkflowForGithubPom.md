Updated .github/workflows/release.yml (+8 -0)

permissions:
  contents: write
+ packages: read

concurrency:
  group: release-${{ github.ref }}

jobs:
  package:
    name: ${{ matrix.runner }} (${{ matrix.javafx }})
+   env:
+     GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
    strategy:
      fail-fast: false
      matrix:

          distribution: temurin
          java-version: '25'
          cache: maven
+         # Writes ~/.m2/settings.xml with credentials for the "github" repository
+         # (GitHub Packages), read from the GITHUB_ACTOR / GITHUB_TOKEN env vars.
+         server-id: github
+         server-username: GITHUB_ACTOR
+         server-password: GITHUB_TOKEN

      - name: Install Maven 3.9.9
        shell: bash

I updated .github/workflows/release.yml so the release build can log in to GitHub Packages. I haven't run it; it only runs when you push a v* tag.

Changes:
- actions/setup-java now has server-id: github, with server-username: GITHUB_ACTOR and server-password: GITHUB_TOKEN. This writes a ~/.m2/settings.xml whose github entry matches the repository id in pom.xml. The two values are names of environment variables, not the secrets themselves.
- Job-level env sets GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }} so Maven can read it. GITHUB_ACTOR is set automatically by Actions.
- permissions now include packages: read next to contents: write.

One thing to check on GitHub: lift-api is published from a different repository, and this repository's built-in token can't read that package by default, even if it's public. On the lift-api package page, go to Package settings → Manage Actions access and add the DictionaryEditor repository with Read access. Without that, the dependency download will fail with a 401 or 403 error. If you'd rather not, a personal access token with read:packages stored as a repository secret would also work.

----

You couldn't find "Package settings → Manage Actions access" because it doesn't exist for lift-api. I was wrong to suggest it: GitHub only offers that setting for container, npm, NuGet and RubyGems packages. Maven packages always take their permissions from the repository that published them. So the DictionaryEditor workflow's built-in token can never read lift-api, and there's no setting that changes that.

The workflow now reads a personal access token from a repository secret named PACKAGES_TOKEN instead. I removed the packages: read permission, since it no longer serves any purpose. You need to do two things on GitHub:

1. Create the token: go to GitHub → your avatar → Settings → Developer settings → Personal access tokens → Tokens (classic) → Generate new token (classic). Tick only the read:packages scope. It has to be a classic token, because fine-grained tokens don't work with the Maven registry.
2. Store it as a secret: in the DictionaryEditor repository, go to Settings → Secrets and variables → Actions → New repository secret. Name it PACKAGES_TOKEN and paste the token as the value.

For reference, a Maven package's own settings page is on your profile's Packages tab, not on the repository page. Yours would be at https://github.com/users/sylvainloiseau/packages/maven/package/io.github.sylvainloiseau.lift-api/settings. It only lets you change visibility or delete the package, so you don't need it here.

I haven't run the workflow; the first v* tag you push after adding the secret will test it.
