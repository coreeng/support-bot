# Integration tests

The support bot is integrated with different external systems such as:
- Kubernetes cluster
- Cloud providers
- Slack

These tests are meant to be testing the bot against these external systems.

Since whether the bot integrated with an external system or not depends on a configuration
that it reads on startup, we need to bootstrap the service multiple times during
the testing process with different configuration provided.

# Steps to run

Open a configuration file under `src/test/resources` folder and change 
the service image repository and the tag, so that you will test against the specific version of the service.

Ensure you have a connection to the external systems you will test against.
At the moment it's:
1. Kubernetes cluster
2. Azure groups

Finally, run the actual tests:
```bash
make test
```
