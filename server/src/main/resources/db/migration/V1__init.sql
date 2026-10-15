create table topics (
    id uuid not null,
    name text not null,
    created_at timestamptz not null default now(),
    constraint pk_topics primary key (id),
    constraint uk_topics_name unique (name)
);

create table concepts (
    id uuid not null,
    topic_id uuid not null,
    name text not null,
    status text not null default 'not_started',
    mastered_at timestamptz,
    next_review_at timestamptz,
    review_interval_days int,
    review_count int not null default 0,
    created_at timestamptz not null default now(),
    constraint pk_concepts primary key (id),
    constraint fk_concepts_topic foreign key (topic_id) references topics (id),
    constraint uk_concepts_topic_name unique (topic_id, name),
    constraint ck_concepts_status check (status in ('not_started', 'reading', 'ready_to_test', 'mastered'))
);

create index idx_concepts_next_review_at on concepts (next_review_at);

create table docs (
    id uuid not null,
    concept_id uuid not null,
    url text not null,
    title text,
    content text not null,
    fetched_at timestamptz not null default now(),
    read_at timestamptz,
    constraint pk_docs primary key (id),
    constraint fk_docs_concept foreign key (concept_id) references concepts (id) on delete cascade
);

create index idx_docs_concept_id on docs (concept_id);
create index idx_docs_read_at on docs (read_at);

create table tests (
    id uuid not null,
    concept_id uuid not null,
    status text not null default 'draft',
    generator_version text not null,
    created_at timestamptz not null default now(),
    constraint pk_tests primary key (id),
    constraint fk_tests_concept foreign key (concept_id) references concepts (id),
    constraint ck_tests_status check (status in ('draft', 'ready'))
);

create index idx_tests_concept_id on tests (concept_id);

create table questions (
    id uuid not null,
    test_id uuid not null,
    position int not null,
    type text not null,
    prompt text not null,
    answer_key text not null,
    rubric jsonb not null,
    wrong_answers jsonb not null default '[]',
    verdict text not null default 'pending',
    verdict_reason text,
    constraint pk_questions primary key (id),
    constraint fk_questions_test foreign key (test_id) references tests (id) on delete cascade,
    constraint uk_questions_test_position unique (test_id, position),
    constraint ck_questions_verdict check (verdict in ('pending', 'ok', 'rejected')),
    constraint ck_questions_type check (type in ('scenario', 'predict_debug', 'what_breaks', 'compare_synthesize', 'explain_why'))
);

create table attempts (
    id uuid not null,
    test_id uuid not null,
    submitted_at timestamptz not null default now(),
    graded_at timestamptz,
    score numeric,
    passed boolean,
    threshold numeric not null,
    grader_version text,
    constraint pk_attempts primary key (id),
    constraint fk_attempts_test foreign key (test_id) references tests (id)
);

create index idx_attempts_test_id on attempts (test_id);
create index idx_attempts_submitted_at on attempts (submitted_at);

create table answers (
    id uuid not null,
    attempt_id uuid not null,
    question_id uuid not null,
    answer_text text not null,
    score numeric,
    criteria_results jsonb,
    feedback text,
    flagged boolean not null default false,
    flag_note text,
    constraint pk_answers primary key (id),
    constraint fk_answers_attempt foreign key (attempt_id) references attempts (id) on delete cascade,
    constraint fk_answers_question foreign key (question_id) references questions (id),
    constraint uk_answers_attempt_question unique (attempt_id, question_id)
);

create index idx_answers_question_id on answers (question_id);
