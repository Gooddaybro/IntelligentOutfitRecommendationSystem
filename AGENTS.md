# Project Coding Instructions

This repository is a Java backend project. Any automated coding agent working here must follow the project comment rules before changing Java backend code.

## Required Comment Policy

- Read `docs/commenting-guidelines.md` before creating or changing Java source, Mapper XML, or backend API contract code.
- Every new top-level Java class, interface, enum, annotation, or record in `src/main/java` must have a Javadoc comment that explains its responsibility and boundary.
- Controller, Service, Mapper, DTO, and Model comments must explain why a design exists, what business boundary it protects, or which cross-service contract it represents. Do not add comments that only repeat the method or field name.
- Core business methods, public APIs, and complex private helpers must have Javadoc when their parameters, return value, exception behavior, or business boundary is not obvious from the signature.
- Inline comments must be placed on the line above the code they explain. Do not write trailing comments after code.
- Use `// TODO: [Owner] Description (Target date or condition)` for planned follow-up work.
- Use `// FIXME: Description of the defect and possible failure mode` for known defects.
- Do not leave large blocks of commented-out code. Use Git history for removed code.

## Verification

- Run `mvnw.cmd verify` on Windows before claiming Java backend changes are complete.
- CI runs Maven `verify`, so Checkstyle violations are treated as build failures.
