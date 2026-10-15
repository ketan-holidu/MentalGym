# Calibration fixtures — Transactional propagation and rollback rules

Three questions from test `22c73893-b2aa-4cb9-b2df-dde5d82089fc`. All three levels were drafted by Claude, which has not seen the answer keys or rubrics — it wrote them from the
same three documents the questions came from. The deep answers are the bar the grader gets measured against, so
they are the ones worth reading closely before submission.

What we check afterwards: per question, shallow < medium < deep, shallow below 80%, deep at or above 80%.

Answers for questions 2, 5 and 6 are submitted as "not part of this calibration round" and left ungraded, so
these attempts stay unfinalized and do not touch the concept's mastery state.

---

## Question 1 (predict_debug) — `e1b76621-b9be-4c16-bb69-e37a276d71fa`

> OrderService.placeOrder() is @Transactional, inserts an order row, then calls AuditService.record() through the
> proxy; record() is @Transactional and throws IllegalStateException; placeOrder() catches it, logs, returns
> normally. What does the caller observe, does the order row persist, what is the mechanism, which propagation
> change would let placeOrder() genuinely absorb the failure, and what is the trade-off?

### shallow

The exception never leaves placeOrder, so Spring has nothing to roll back on. The order row is committed and the
audit row is not, because the audit method threw before it wrote anything. Rollback only happens when an exception
propagates out of the transactional method, and here it is caught. If you want the audit failure to be absorbed
properly you can put REQUIRES_NEW on record() so it runs separately. The trade-off is a bit more overhead per call.

### medium

Because record() uses the default REQUIRED propagation, it joins the transaction placeOrder already started — there
is one physical transaction, not two. When record() throws, Spring marks that shared transaction rollback-only.
placeOrder swallowing the exception does not clear that flag, so when the outer transaction tries to commit, the
commit is refused and the caller gets an UnexpectedRollbackException instead of a normal return. The order row is
not in the database.

To really absorb the failure, annotate record() with REQUIRES_NEW so it runs in its own physical transaction; its
rollback then has no effect on the order transaction. The trade-off is that it needs a second database connection
while the outer transaction is suspended.

### deep

Nothing about the try/catch changes the outcome: the caller does not get a normal return, it gets an
UnexpectedRollbackException, and there is no order row in the database.

Mechanism. record() uses the default propagation REQUIRED, so it does not start a transaction of its own — it
participates in the one placeOrder already opened. Both methods are separate *logical* transaction scopes mapped
onto a single *physical* transaction and a single connection. When record() throws IllegalStateException, the
interceptor around it sees an unchecked exception and, because its scope is only a participant, it cannot roll
anything back by itself; what it can do is set the rollback-only marker on the shared physical transaction.
placeOrder catching the exception removes the exception, not the marker. So placeOrder runs to the end and the
outer interceptor calls commit — and the transaction manager refuses, rolls back instead, and throws
UnexpectedRollbackException. The docs are explicit that this is deliberate: the caller must never be able to
believe a commit happened when it did not. The insert is undone with everything else in that physical transaction.

The propagation change: put REQUIRES_NEW on record(). It then always gets its own independent physical
transaction; the outer one is suspended for the duration and its resources stay bound to it while the inner one
acquires a second connection. The audit rollback is then local to that inner transaction, no marker reaches the
order transaction, and placeOrder's catch genuinely absorbs the failure.

The trade-off is real, and in several directions. Each nested call holds two connections at once on the same
thread, so the pool has to be sized for twice the concurrency or the import deadlocks on connection acquisition.
Atomicity is gone by design: a successful audit row commits on its own, so an order that fails later leaves an
audit record of something that never happened. And the inner transaction cannot see the outer transaction's
uncommitted writes — it is a separate transaction on a separate connection — so an audit that tries to read the
order row it is auditing will not find it, and may block on its locks instead.

If what I actually want is "undo the audit attempt but keep the order", NESTED is the closer fit: one physical
transaction, a savepoint per inner scope, roll back to the savepoint and carry on. It costs no second connection
but it only works on JDBC resources that support savepoints.

---

## Question 3 (compare_synthesize) — `4f8a5b0d-6037-49f9-b024-bd81cf7e3496`

> 500 CSV rows, bad rows skipped with their partial writes undone, per-row timeout of 2s. Compare an outer
> @Transactional importAll() calling a per-row method with (A) NESTED + timeout 2 versus (B) REQUIRES_NEW +
> timeout 2 on: physical transactions and connections; what happens to the 499 good rows if importAll() fails
> after the loop; whether timeout = 2 takes effect; what each needs from the transaction manager / resource.
> Then pick one for all-or-nothing apart from skipped bad rows.

### shallow

Both options let you skip a bad row without killing the import, they just do it differently: NESTED nests the row
transaction inside the outer one, REQUIRES_NEW starts a fresh one for each row. Either way the bad row's writes are
undone and the loop carries on. The timeout of 2 seconds applies to the per-row method in both designs, so a slow
row is cut off. If importAll fails later, the rows that already succeeded are gone in both cases because the outer
transaction wraps everything. I would pick REQUIRES_NEW because it is the usual way to isolate a failing row and it
works with every transaction manager.

### medium

A) NESTED runs inside the outer transaction using savepoints: one physical transaction, one connection, and rolling
back a bad row just rolls back to its savepoint. Because everything is still one transaction, a failure in
importAll() after the loop undoes all 499 good rows too.

B) REQUIRES_NEW suspends the outer transaction and starts a real new one per row: 501 physical transactions and a
second connection held while each row runs. The 499 good rows are already committed, so a later failure in
importAll() leaves them in the database.

The timeout behaves differently between them — a brand new transaction can carry its own timeout, while a nested
one is bounded by the transaction that is already running. NESTED needs a resource that supports savepoints.

For all-or-nothing apart from skipped bad rows, NESTED is the right pick: bad rows are undone individually and a
late failure still takes the whole import with it.

### deep

**A) NESTED, timeout = 2**

1. One physical transaction and one connection for the whole import. Each per-row call opens a logical scope that
   is mapped to a JDBC savepoint, so a bad row rolls back to its savepoint and the physical transaction continues.
2. If importAll() fails after the loop, all 499 good rows go with it. They were never independently committed —
   they are writes in the one physical transaction that is now rolling back.
3. The declared timeout = 2 does **not** take effect. A participating scope joins the characteristics of the outer
   scope and Spring silently ignores the local isolation level, timeout and read-only flag. A savepoint is not a
   transaction and has nothing to hang a timeout on. (With validateExistingTransaction = true you at least get the
   mismatch rejected loudly instead of ignored, for isolation and read-only.)
4. It needs savepoints: a JDBC resource transaction with DataSourceTransactionManager and a driver that supports
   them. It is not generally available under JTA.

**B) REQUIRES_NEW, timeout = 2**

1. 501 physical transactions — the outer one plus one per row — and two connections held at once per import
   thread, because the outer transaction keeps its resources bound while it is suspended and the inner one
   acquires its own connection.
2. If importAll() fails after the loop, the 499 good rows stay. They were committed independently as the loop ran,
   and the outer rollback cannot reach them.
3. The timeout = 2 does take effect. An independent physical transaction declares its own isolation level, timeout
   and read-only setting instead of inheriting the outer ones.
4. It needs a transaction manager that can suspend and resume, which both JTA and DataSourceTransactionManager
   can do. No savepoint support required.

**Pick: A, NESTED.** The requirement is all-or-nothing apart from the skipped bad rows, and that is exactly the
shape of one physical transaction with per-row savepoints: a bad row is undone on its own, and a failure after the
loop still takes the whole import down. B fails the requirement outright — it leaves 499 committed rows behind
after a failure that was supposed to undo everything, and no amount of compensating code makes that atomic again.

What I give up with A is the per-row timeout, since it is silently ignored. If a slow row really has to be cut
off, that belongs on the statement (a JDBC query timeout) or as a single timeout on importAll(), not on the nested
scope — and I would rather lose the per-row timeout than lose the atomicity requirement the feature was for.

---

## Question 4 (scenario) — `31fffed3-ac14-4627-bc98-c8ddd0ee9497`

> Kotlin, Spring Framework 6.2. @Transactional fun settle() inserts a debit row, calls the gateway, and throws
> SettlementFailedException : Exception when declined. Declined settlements leave the debit row behind. Why, and
> by which rule? Give a method-scoped fix and a global fix, and say what the global fix does to methods that
> already declare rollbackFor/noRollbackFor. Why is this bug especially easy to introduce in Kotlin?

### shallow

The debit row stays because the write to the ledger already happened before the exception was thrown, and the
external gateway call is not part of the transaction, so there is nothing to undo by the time it fails. The fix on
the method is to add rollbackFor = Exception::class to @Transactional. Globally you would configure the transaction
manager to roll back on every exception, which then applies everywhere. In Kotlin this is easy to hit because
exceptions are not declared, so you do not notice which ones are thrown.

### medium

Spring's default rollback rule only rolls back for unchecked exceptions — RuntimeException and Error.
SettlementFailedException extends Exception, so it is a checked exception, and the transaction commits on the way
out even though the caller sees the exception. That is why the debit row survives.

Scoped fix: @Transactional(rollbackFor = [SettlementFailedException::class]) on settle(), or make the exception
extend RuntimeException instead.

Global fix: Spring Framework 6.2 lets you change the default so that all exceptions roll back, instead of only
unchecked ones.

It is easy to hit in Kotlin because Kotlin has no checked exceptions, so extending Exception feels like the neutral
choice and nothing in the compiler points out that Spring treats it differently from RuntimeException.

### deep

The rule is Spring's default rollback rule: the transaction is rolled back on RuntimeException and Error, and
committed on everything else, including checked exceptions. SettlementFailedException extends Exception, not
RuntimeException, so it is a checked exception as far as that rule is concerned. The interceptor sees an exception
it has no rollback rule for, commits the transaction on the way out, and rethrows. The caller sees
SettlementFailedException and the committed debit row — the exception reaching the caller and the transaction
rolling back are two independent things, and only the second one is governed by the rollback rules.

Scoped fix: declare the rule on the method, @Transactional(rollbackFor = [SettlementFailedException::class]).
Making the exception extend RuntimeException would also do it, but that changes the exception's contract for every
other caller, so the annotation is the honest fix for one method.

Global fix: as of 6.2, @EnableTransactionManagement(rollbackOn = ALL_EXCEPTIONS), which makes every exception
raised in a transaction roll back, checked ones included. (AnnotationTransactionAttributeSource.addDefaultRollbackRule
is there for finer custom defaults.)

What that does to methods with their own rules: it changes the *default*, not their declarations. A method that
declares rollbackFor or noRollbackFor keeps its own rules, and those rules win for the exceptions they name — a
method with noRollbackFor = [SomeBusinessException::class] still commits on that exception under ALL_EXCEPTIONS.
The chosen default only governs the exceptions a method's own rules do not mention. So the global switch is safe
to flip in the sense that it cannot silently override a deliberate local decision, but it will change behaviour
for every method that was relying on the unchecked-only default without saying so.

Why Kotlin makes it easy to introduce: Kotlin has no checked exceptions, so nothing in the language or the
compiler distinguishes `: Exception` from `: RuntimeException` — there is no throws clause to write and no warning
to ignore. `class SettlementFailedException(msg: String) : Exception(msg)` is the natural thing to type, and it
silently lands on the commit side of a JVM-level rule the language itself does not have. The reference
documentation makes exactly this point: it advises switching to ALL_EXCEPTIONS for Kotlin applications, where
there is no enforcement of checked exceptions to make the distinction visible.
