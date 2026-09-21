# Publishing the source to GitHub

The public repository should contain source code, example configuration and documentation. Running services requires the separate Docker or Kubernetes deployment steps.

## Before publishing

```bash
git status --short --ignored
git add .
python3 scripts/check-publication.py
git diff --cached --stat
git diff --cached
```

The publication check reads the Git index, including the exact staged contents. It rejects generated/private files, machine-specific filesystem paths, private-key blocks and common credential formats. It is a focused check and does not guarantee detection of every possible secret.

The local .env file, Maven targets, Python caches, logs and generated manifests are ignored. The Docker build context has an allowlist containing only Maven descriptors, application sources and the Dockerfile.

Keep localhost/loopback addresses in local instructions and health checks. Container-to-container connections use service names; environment variables and the production example supply deployment-specific endpoints.

## Create a public repository

Use a GitHub account with permission to create repositories. Sign in through the official CLI flow; never paste an access token into source files or issue comments.

```bash
gh auth login
gh auth status
gh repo create YOUR_OWNER/springcloud-enterprise-demo --public --source=. --remote=origin --push --description "Spring Cloud commerce reference with Docker, Kubernetes and deployment scripts"
```

Replace YOUR_OWNER with your GitHub username or an organization you can administer. The source must already have an initial commit. If a matching repository or origin remote exists, inspect it first instead of overwriting it.

The GitHub Actions workflow runs after publication. Review its result before treating the Linux container build as verified. Publishing the repository does not deploy services or configure a Kubernetes cluster.

No license has been selected for this project. The maintainer should choose one explicitly before describing the project as licensed open source.
