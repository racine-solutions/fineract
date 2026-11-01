# Disabled upstream workflows

These are Apache Fineract's own CI workflows. They are kept here, outside `.github/workflows/`, so GitHub does not run
them on the Racine fork: they are heavy (integration test matrices, Sonar, DockerHub publishing), some only work in the
Apache repository (PR title and signed-commit checks), and running them all on every push contributed to GitHub
disabling Actions for this repository.

The only active workflow is `.github/workflows/racine-docker-build-push.yml`, which builds and pushes the Racine image.

To run one of these again, move it back into `.github/workflows/`.
