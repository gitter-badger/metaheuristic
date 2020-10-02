CREATE INDEX mh_task_processor_id_idx
    ON mh_task (PROCESSOR_ID);

alter table mh_batch modify EXEC_CONTEXT_ID decimal not null;

delete from mh_experiment where EXEC_CONTEXT_ID is null;

alter table mh_experiment modify EXEC_CONTEXT_ID decimal not null;



