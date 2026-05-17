# tests/

JUnit 5 test suite for `RingBuffer`.

## Running the tests

From the **repo root**:

```
mvn test
```

## Package note

All test sources are in the **default Java package** (no `package` declaration).
This is required because `RingBuffer` and its inner classes are also in the default package; Java does not allow a named package to access default-package types.

## Known failing test

`OoStructuralContractTest.writerHasNoPublicConstructor` — the production `Writer` class exposes a public constructor, which violates the expected singleton contract. This is a documented defect in the production code, not a bug in the test.

## Traceability

Full spec (requirements, design, tasks): [`.kiro/specs/ringbuffer-unit-tests/`](../.kiro/specs/ringbuffer-unit-tests/)

Behaviors not covered by this suite: [`UNTESTABLE_ISSUES.md`](UNTESTABLE_ISSUES.md)
