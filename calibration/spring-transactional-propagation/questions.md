# Calibration test — Transactional propagation and rollback rules

testId `22c73893-b2aa-4cb9-b2df-dde5d82089fc` · conceptId `156f4a3f-75fe-49e8-b4b4-0f88989e68a1` · 6 questions

## Question 1 (predict_debug)

`questionId e1b76621-b9be-4c16-bb69-e37a276d71fa`

Two Spring beans, both using the default @Transactional settings (proxy mode, JdbcTransactionManager):

OrderService.placeOrder() is @Transactional. It inserts an order row, then calls auditService.record(...) through the injected AuditService proxy. AuditService.record() is @Transactional and throws an IllegalStateException. placeOrder() wraps that call in try/catch, swallows the IllegalStateException, logs a warning, and returns normally.

No exception escapes placeOrder() from the application code's point of view. Describe exactly what the caller of placeOrder() observes, whether the order row ends up in the database, and explain the mechanism that produces this outcome. Then name one change to the propagation setting that would let placeOrder() genuinely absorb the failure, and say what trade-off that change introduces.

## Question 2 (what_breaks)

`questionId 7bdb610f-7e7f-4bfd-993c-1fc26524fd55`

A reporting endpoint is served by BatchService.runBatch(), annotated @Transactional (defaults). For each of several items it calls, through the proxy, ItemService.process(item), which is also @Transactional with defaults. Everything runs on a DataSourceTransactionManager over a HikariCP pool with maximumPoolSize = 8. The service is called by up to 8 concurrent request threads and has been stable in production for a year.

A developer changes only one thing: ItemService.process() becomes @Transactional(propagation = Propagation.REQUIRES_NEW).

Describe what starts failing under concurrent load, trace the mechanism step by step, and state the sizing rule that would make the new configuration safe. Also name one thing the change genuinely improves.

## Question 3 (compare_synthesize)

`questionId 4f8a5b0d-6037-49f9-b024-bd81cf7e3496`

You must import 500 CSV rows. A bad row must be skipped (its partial writes undone) while the import continues, and a per-row import step should ideally run with a short timeout of its own. Two designs are on the table, both with an outer @Transactional importAll() calling a per-row method on another bean:

A) per-row method @Transactional(propagation = Propagation.NESTED, timeout = 2)
B) per-row method @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 2)

Compare the two on: (1) how many physical transactions and database connections are involved, (2) what happens to the 499 good rows if importAll() itself fails after the loop, (3) whether the declared timeout = 2 actually takes effect, and (4) what each design requires of the underlying transaction manager / resource. Then say which you would pick if the whole import must be all-or-nothing apart from skipped bad rows, and why.

## Question 4 (scenario)

`questionId 31fffed3-ac14-4627-bc98-c8ddd0ee9497`

A Kotlin payment service on Spring Framework 6.2 has:

@Transactional
fun settle(id: Long) {
    ledger.insertDebit(id)          // writes a row
    val ok = gateway.charge(id)     // external call
    if (!ok) throw SettlementFailedException("declined")   // class SettlementFailedException(msg: String) : Exception(msg)
}

In production, declined settlements leave a debit row in the ledger even though an exception reached the caller. Explain precisely why, giving the rule that decides it. Then give two different ways to fix it — one scoped to this method, one applied globally — and explain, for the global fix, what happens to methods that already declare their own rollbackFor/noRollbackFor rules. Finally, say why this bug is especially easy to introduce in a Kotlin codebase.

## Question 5 (explain_why)

`questionId 71d269d2-4c8e-48a2-ac12-3b84cf995a7d`

A team reports three separate incidents on one Spring application running with default transaction configuration:

1. ImportService.importAll() (no annotation) loops over rows calling this.importOne(row); importOne is annotated @Transactional(propagation = Propagation.REQUIRES_NEW), yet no per-row transaction is ever created.
2. CacheWarmer has a @PostConstruct method that calls its own @Transactional loadAll(); the writes are not transactional.
3. After migrating the build to AspectJ load-time weaving, a service whose @Transactional annotations live only on the interface it implements stops rolling back entirely — unit tests had been green for months.

Explain the single design decision that causes 1 and 2, and the separate reason for 3. Then argue why Spring's default behaviour here is a reasonable trade-off rather than a bug, what the alternative costs, and why the documentation warns that your code may 'appear to work until you test a rollback scenario'.

## Question 6 (predict_debug)

`questionId 56fe29ad-02a2-4e2f-8359-cfda9a8f6b96`

Proxy mode, a single DataSourceTransactionManager with default manager settings (validateExistingTransaction = false). Two beans:

@Transactional(readOnly = true, timeout = 30)
class ReportService {
    @Transactional(
        readOnly = false,
        propagation = Propagation.REQUIRED,
        isolation = Isolation.REPEATABLE_READ,
        timeout = 5
    )
    fun summary(id: Long): Summary {
        // several reads, then writes one cached_summary row
    }
}

class ArchiveService {
    @Transactional(readOnly = true)   // propagation REQUIRED, ISOLATION_DEFAULT, no timeout
    fun archive(id: Long) {
        val s = reportService.summary(id)   // call through the injected ReportService proxy
        // ... further reads
    }
}

(a) A controller calls reportService.summary(id) directly. State which propagation, isolation, timeout and read-only setting actually govern that invocation, and the rule that decides it between the class-level and the method-level annotation.

(b) A controller instead calls archiveService.archive(id). State what governs summary() on this path, and say specifically what becomes of its declared isolation level, timeout value and read-only flag, and how many physical transactions exist.

(c) The team sets validateExistingTransaction = true on the transaction manager and re-runs the call path from (b). Say what changes, and name the direction of read-only mismatch that this non-lenient mode rejects.

(d) Give the single propagation change on summary() that would make its own declared isolation, timeout and read-only settings take effect on both call paths, and state two concrete costs of making that change.
