# RQ3: cross-project transfer targets

Data for RQ3 (transformation generalizability) of the BigBag paper.

- `RQ3_transfer_targets.xlsx`: the same data as the CSV files, one sheet per file.
- `README.csv`: definitions of the columns and of the transfer results.
- `Breaking_updates.csv`: the 32 breaking dependency updates (BU01 to BU32, as in Table III), with seed project, reproduced and fixed clients, and clients passing `mvn test`.
- `Transfer_targets.csv`: the 1,160 verified transfer targets, with the search that found each one, the search date, the client commit, fork information, the transfer result (FIXED, NO_MATCH, INCOMPLETE_MIGRATION, TRANSFORMATION_ERROR) and the `mvn test` result of fixed clients.
- `Queries.csv`: the GitHub search queries per breaking dependency update, with dates and result counts where they were logged.

The searches ran between 2026-04-21 and 2026-10-20, over all public repositories indexed by GitHub, with no filter on creation date, activity date, stars, size, archival status or forks. We kept one candidate per repository and breaking dependency update.
