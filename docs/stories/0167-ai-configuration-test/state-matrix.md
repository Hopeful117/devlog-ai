# Story 0167 Configuration-Test State Matrix

| Test result | Derived configuration state |
|---|---|
| `SUCCESS` | `READY` when the association remains authorized |
| `CREDENTIAL_INVALID` | `CREDENTIAL_INVALID` |
| `MODEL_UNAVAILABLE` | `MODEL_UNAVAILABLE` |
| `PROVIDER_UNAVAILABLE` | `PROVIDER_UNAVAILABLE` |
| `TIMEOUT` | `PROVIDER_UNAVAILABLE` |
| `CONFIGURATION_INVALID` | `CONFIGURATION_INCOMPLETE` |

A failed test is persisted against the exact configuration-test identity and
cannot leave a configuration reported as `READY`. Saving a changed provider,
authentication method, model, or parameter set clears the previous test result.
