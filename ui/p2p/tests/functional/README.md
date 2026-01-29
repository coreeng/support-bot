# Functional Tests

End-to-end functional tests for the Support UI using Cucumber.js and Playwright.

## Prerequisites

- The Next.js app must be running locally (`yarn dev` from the root directory)

## Running Tests

- Support Bot Backend needs to run. Otherwise, if you'd like to run
with a WireMock backend, you can:
`cd` to the `support-ui` directory, and execute:
```
source .venv-wiremock/bin/activate  # or create/activate your venv
python3 - <<'PY'
import yaml, os, textwrap, pathlib
docs = list(yaml.safe_load_all(open("p2p/tests/mock-backend-wiremock.yaml")))
cm = next(d for d in docs if isinstance(d, dict) and 'data' in d)
out_dir = pathlib.Path("p2p/tests/mock-backend-wiremock.d")
out_dir.mkdir(parents=True, exist_ok=True)
for name, content in cm["data"].items():
    fname = name.strip('"')
    with open(out_dir / fname, "w") as f:
        f.write(textwrap.dedent(content.strip() + "\n"))
    print("wrote", out_dir / fname)
PY
```

Then run wiremock in Docker:

```
docker run --rm -it -p 8080:8080 \
  -v "$PWD/p2p/tests/mock-backend-wiremock.d:/home/wiremock/mappings" \
  wiremock/wiremock:3.3.1 --verbose
```

**Note:** All commands below assume you're in the `p2p/tests/functional` directory.

### Install Dependencies
```bash
cd p2p/tests/functional
yarn install
```

### Run All Tests (Parallel Mode - Fast!)
```bash
# Runs tests in parallel (3 workers)
yarn test:cucumber

# Run with 4 workers for even faster execution
yarn test:cucumber:fast
```

### Run a Specific Feature File
```bash
NODE_ENV=test npx cucumber-js tests/features/dashboards.feature
NODE_ENV=test npx cucumber-js tests/features/tickets.feature
```

### Run a Specific Scenario (by line number)
```bash
npx cucumber-js tests/features/dashboards.feature:6
npx cucumber-js tests/features/tickets.feature:15
```

### Run from Project Root
```bash
npx cucumber-js p2p/tests/functional/tests/features/
```

### Debug Mode
Set `PWDEBUG=1` to run in headed mode with Playwright Inspector:
```bash
NODE_ENV=test PWDEBUG=1 yarn test:cucumber
```

