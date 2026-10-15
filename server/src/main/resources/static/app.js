const view = document.getElementById('view');
const headerNote = document.getElementById('header-note');

const esc = (value) => String(value ?? '').replace(/[&<>"']/g, (c) => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]
));

const percent = (score) => (score === null || score === undefined ? '—' : `${Math.round(score * 100)}%`);

const dateOf = (value) => (value ? new Date(value).toLocaleDateString(undefined, { day: '2-digit', month: 'short', year: 'numeric' }) : '—');

const dateTimeOf = (value) => (value ? new Date(value).toLocaleString(undefined, { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' }) : '—');

const statusLabel = (concept) => {
    const due = concept.dueForReview;
    const cls = due ? 'status-due' : `status-${concept.status}`;
    const text = due ? 'due for review' : concept.status.replace(/_/g, ' ');
    return `<span class="status ${cls}">${esc(text)}</span>`;
};

const get = async (path) => {
    const response = await fetch(path);
    if (!response.ok) throw new Error(`${path} returned ${response.status}`);
    return response.json();
};

function trendSvg(points) {
    const scored = points.filter((point) => point.score !== null && point.score !== undefined);
    if (scored.length === 0) return '<p class="empty">No graded attempts yet.</p>';
    const width = 640;
    const height = 160;
    const padding = 28;
    const step = scored.length > 1 ? (width - padding * 2) / (scored.length - 1) : 0;
    const y = (score) => height - padding - score * (height - padding * 2);
    const coords = scored.map((point, index) => [padding + step * index, y(point.score)]);
    const line = coords.map(([px, py], index) => `${index === 0 ? 'M' : 'L'}${px.toFixed(1)},${py.toFixed(1)}`).join(' ');
    const dots = coords.map(([px, py], index) => {
        const colour = scored[index].passed ? 'var(--ok)' : 'var(--bad)';
        return `<circle cx="${px.toFixed(1)}" cy="${py.toFixed(1)}" r="4" fill="${colour}"><title>${esc(dateTimeOf(scored[index].submittedAt))}: ${percent(scored[index].score)}</title></circle>`;
    }).join('');
    return `<svg class="trend" viewBox="0 0 ${width} ${height}" width="100%" height="${height}">
        <line x1="${padding}" y1="${y(1)}" x2="${width - padding}" y2="${y(1)}" stroke="var(--line)" />
        <line x1="${padding}" y1="${y(0)}" x2="${width - padding}" y2="${y(0)}" stroke="var(--line)" />
        <text x="4" y="${y(1) + 4}" fill="var(--muted)" font-size="11">100%</text>
        <text x="4" y="${y(0) + 4}" fill="var(--muted)" font-size="11">0%</text>
        <path d="${line}" fill="none" stroke="var(--accent)" stroke-width="2" />
        ${dots}
    </svg>`;
}

async function renderOverview() {
    const [stats, topics] = await Promise.all([get('/api/stats'), get('/api/concepts')]);
    headerNote.textContent = `pass threshold ${Math.round(stats.passThreshold * 100)}%`;

    const counts = stats.conceptsByStatus;
    const cards = [
        ['Streak', `${stats.streakDays}d`, `longest ${stats.longestStreakDays}d`],
        ['Due today', stats.dueNow.length, stats.lastActivityDay ? `last activity ${stats.lastActivityDay}` : 'no activity yet'],
        ['Mastered', counts.mastered ?? 0, `${stats.totals.concepts} concepts`],
        ['Ready to test', counts.ready_to_test ?? 0, `${counts.reading ?? 0} reading`],
        ['Attempts', stats.totals.attempts, `${stats.totals.flaggedAnswers} flagged`],
    ].map(([label, value, note]) => `
        <div class="card">
            <div class="label">${esc(label)}</div>
            <div class="value">${esc(value)}</div>
            <div class="label">${esc(note)}</div>
        </div>`).join('');

    const due = stats.dueNow.length === 0
        ? '<p class="empty">Nothing due. Add a concept or read ahead.</p>'
        : `<table><thead><tr><th>Concept</th><th>Topic</th><th>Due since</th></tr></thead><tbody>${
            stats.dueNow.map((concept) => `<tr>
                <td><a href="#/concept/${concept.conceptId}">${esc(concept.name)}</a></td>
                <td>${esc(concept.topic)}</td>
                <td>${esc(dateOf(concept.nextReviewAt))} (${concept.daysOverdue}d)</td>
            </tr>`).join('')
        }</tbody></table>`;

    const byTopic = topics.map((group) => `
        <h3>${esc(group.topic)}</h3>
        <table><thead><tr><th>Concept</th><th>Status</th><th>Docs</th><th>Last score</th><th>Next review</th></tr></thead><tbody>${
        group.concepts.map((concept) => `<tr>
            <td><a href="#/concept/${concept.conceptId}">${esc(concept.name)}</a></td>
            <td>${statusLabel(concept)}</td>
            <td>${concept.docCount}${concept.docsRead ? ' read' : ''}</td>
            <td class="${concept.lastAttemptPassed === true ? 'pass' : concept.lastAttemptPassed === false ? 'fail' : 'muted'}">${percent(concept.lastScore)}</td>
            <td>${esc(dateOf(concept.nextReviewAt))}</td>
        </tr>`).join('')
    }</tbody></table>`).join('');

    const recent = stats.recentAttempts.length === 0
        ? '<p class="empty">No attempts yet.</p>'
        : `<table><thead><tr><th>Concept</th><th>Submitted</th><th>Score</th></tr></thead><tbody>${
            stats.recentAttempts.map((attempt) => `<tr>
                <td><a href="#/concept/${attempt.conceptId}">${esc(attempt.conceptName)}</a></td>
                <td>${esc(dateTimeOf(attempt.submittedAt))}</td>
                <td class="${attempt.passed === true ? 'pass' : attempt.passed === false ? 'fail' : 'muted'}">${attempt.status === 'graded' ? percent(attempt.score) : 'grading'}</td>
            </tr>`).join('')
        }</tbody></table>`;

    view.innerHTML = `
        <div class="cards">${cards}</div>
        <h2>Due for review</h2><div class="panel">${due}</div>
        <h2>Topics</h2><div class="panel">${byTopic || '<p class="empty">No concepts yet.</p>'}</div>
        <h2>Recent attempts</h2><div class="panel">${recent}</div>`;
}

function answerBlock(answer) {
    const missed = answer.missedCriteria.length === 0
        ? '<li class="pass">every criterion met</li>'
        : answer.missedCriteria.map((criterion) => `<li>${esc(criterion.text)} <span class="muted">(${criterion.kind}, ${percent(criterion.credit)} credit)</span></li>`).join('');
    const key = answer.answerKey ? `<h3>Answer key</h3><div class="answer">${esc(answer.answerKey)}</div>` : '';
    const flag = answer.flagged ? `<p class="fail">Flagged: ${esc(answer.flagNote)}</p>` : '';
    return `<div class="panel">
        <h3>Question ${answer.position} · ${esc(answer.type)} · <span class="${answer.score >= 0.8 ? 'pass' : 'fail'}">${percent(answer.score)}</span>${answer.misconceptionFlagged ? ' · <span class="fail">misconception</span>' : ''}</h3>
        <p>${esc(answer.prompt)}</p>
        <h3>Your answer</h3>
        <div class="answer">${esc(answer.learnerAnswer)}</div>
        <h3>Feedback</h3>
        <p>${esc(answer.feedback ?? '—')}</p>
        <h3>Missed criteria</h3>
        <ul class="criteria">${missed}</ul>
        ${key}
        ${flag}
    </div>`;
}

async function renderConcept(conceptId) {
    const [payload, trend] = await Promise.all([
        get(`/api/concepts/${conceptId}`),
        get(`/api/concepts/${conceptId}/trend`),
    ]);
    const { detail, latestResult, flagged } = payload;
    const concept = detail.concept;
    headerNote.textContent = `${concept.topic} · ${concept.name}`;

    const docs = detail.docs.length === 0
        ? '<p class="empty">No docs stored yet.</p>'
        : `<table><thead><tr><th>Doc</th><th>Read</th><th>Size</th><th>Fetched</th></tr></thead><tbody>${
            detail.docs.map((doc) => `<tr>
                <td><a href="${esc(doc.url)}" target="_blank" rel="noreferrer">${esc(doc.title || doc.url)}</a></td>
                <td>${doc.read ? 'yes' : 'no'}</td>
                <td class="mono">${doc.characters.toLocaleString()} chars</td>
                <td>${esc(dateOf(doc.fetchedAt))}</td>
            </tr>`).join('')
        }</tbody></table>`;

    const attempts = detail.attempts.length === 0
        ? '<p class="empty">No attempts yet.</p>'
        : `<table><thead><tr><th>Submitted</th><th>Status</th><th>Score</th><th>Threshold</th></tr></thead><tbody>${
            detail.attempts.slice().reverse().map((attempt) => `<tr>
                <td>${esc(dateTimeOf(attempt.submittedAt))}</td>
                <td>${esc(attempt.status)}</td>
                <td class="${attempt.passed === true ? 'pass' : attempt.passed === false ? 'fail' : 'muted'}">${percent(attempt.score)}</td>
                <td class="muted">${percent(attempt.threshold)}</td>
            </tr>`).join('')
        }</tbody></table>`;

    const latest = latestResult && latestResult.status === 'graded'
        ? latestResult.answers.map(answerBlock).join('')
        : '<p class="empty">The latest attempt has no grades yet.</p>';

    const flags = flagged.length === 0
        ? ''
        : `<h2>Flagged grades</h2><div class="panel"><ul class="criteria">${
            flagged.map((row) => `<li>Question ${row.position} (${esc(dateOf(row.submittedAt))}): ${esc(row.note)}</li>`).join('')
        }</ul></div>`;

    view.innerHTML = `
        <h2>${esc(concept.name)} ${statusLabel(concept)}</h2>
        <div class="cards">
            <div class="card"><div class="label">Last score</div><div class="value">${percent(concept.lastScore)}</div><div class="label">${esc(dateOf(concept.lastAttemptAt))}</div></div>
            <div class="card"><div class="label">Reviews passed</div><div class="value">${concept.reviewCount}</div><div class="label">next ${esc(dateOf(concept.nextReviewAt))}</div></div>
            <div class="card"><div class="label">Docs</div><div class="value">${concept.docCount}</div><div class="label">${concept.docsRead ? 'all read' : 'unread'}</div></div>
        </div>
        <h2>Score trend</h2><div class="panel">${trendSvg(trend)}</div>
        <h2>Docs</h2><div class="panel">${docs}</div>
        <h2>Attempts</h2><div class="panel">${attempts}</div>
        ${flags}
        <h2>Last test</h2>${latest}`;
}

async function route() {
    const match = location.hash.match(/^#\/concept\/([0-9a-f-]+)$/i);
    try {
        view.textContent = 'Loading…';
        if (match) await renderConcept(match[1]);
        else await renderOverview();
    } catch (error) {
        view.innerHTML = `<p class="fail">${esc(error.message)}</p><p class="muted">Is the stack running? docker compose up</p>`;
    }
}

window.addEventListener('hashchange', route);
route();
