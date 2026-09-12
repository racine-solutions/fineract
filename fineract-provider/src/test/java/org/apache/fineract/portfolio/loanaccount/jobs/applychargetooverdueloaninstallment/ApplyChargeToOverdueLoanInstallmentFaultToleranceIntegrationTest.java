/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.portfolio.loanaccount.jobs.applychargetooverdueloaninstallment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.JobRepositoryFactoryBean;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Proves - against a real, transactional {@link Step} built the same way as
 * {@link ApplyChargeToOverdueLoanInstallmentConfig} (chunk + faultTolerant + skip, with
 * {@link ApplyChargeToOverdueLoanInstallmentStepExecutionListener} attached) - that when some items in a chunk fail,
 * Spring Batch does NOT roll back the chunk's other, successfully-processed items. Instead it re-processes the chunk
 * item-by-item, commits every item that succeeds, and skips only the one(s) that fail.
 *
 * This uses a plain scratch table (not the real loan schema) on an in-memory H2 database so the chunk's writes are
 * genuinely transactional - only that lets a test tell the difference between "rolled back" and "never attempted".
 */
public class ApplyChargeToOverdueLoanInstallmentFaultToleranceIntegrationTest {

    private static final Set<Long> IDS_THAT_FAIL_IN_PROCESSOR = Set.of(3L);
    private static final Set<Long> IDS_THAT_FAIL_IN_WRITER = Set.of(8L);

    private DataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private JobRepository jobRepository;
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    public void setUp() throws Exception {
        dataSource = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:org/springframework/batch/core/schema-drop-h2.sql")
                .addScript("classpath:org/springframework/batch/core/schema-h2.sql").build();
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("create table processed_loans (loan_id bigint primary key)");

        transactionManager = new DataSourceTransactionManager(dataSource);
        JobRepositoryFactoryBean jobRepositoryFactoryBean = new JobRepositoryFactoryBean();
        jobRepositoryFactoryBean.setDataSource(dataSource);
        jobRepositoryFactoryBean.setTransactionManager(transactionManager);
        jobRepositoryFactoryBean.afterPropertiesSet();
        jobRepository = jobRepositoryFactoryBean.getObject();
    }

    @Test
    public void testChunkFailures_DoNotRollBackTheOtherSuccessfulItemsInTheChunk() throws Exception {
        final List<Long> loanIds = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);

        final Step step = new StepBuilder("applyChargeToOverdueLoanInstallmentStep-test", jobRepository)
                .<Long, Long>chunk(5, transactionManager) //
                .reader(new ListItemReader<>(loanIds)) //
                .processor((ItemProcessor<Long, Long>) loanId -> {
                    if (IDS_THAT_FAIL_IN_PROCESSOR.contains(loanId)) {
                        throw new IllegalStateException("Simulated failure applying penalty charge for loan " + loanId);
                    }
                    return loanId;
                }) //
                .writer((ItemWriter<Long>) (Chunk<? extends Long> chunk) -> {
                    for (final Long loanId : chunk) {
                        if (IDS_THAT_FAIL_IN_WRITER.contains(loanId)) {
                            throw new IllegalStateException("Simulated failure applying penalty charge for loan " + loanId);
                        }
                        jdbcTemplate.update("insert into processed_loans (loan_id) values (?)", loanId);
                    }
                }) //
                .faultTolerant() //
                .skip(Exception.class) //
                .skipLimit(Integer.MAX_VALUE) //
                .listener(new ApplyChargeToOverdueLoanInstallmentStepExecutionListener()) //
                .build();

        final Job job = new JobBuilder("applyChargeToOverdueLoanInstallmentsJob-test", jobRepository).start(step)
                .incrementer(new RunIdIncrementer()).build();

        final TaskExecutorJobLauncher jobLauncher = new TaskExecutorJobLauncher();
        jobLauncher.setJobRepository(jobRepository);
        jobLauncher.setTaskExecutor(new SyncTaskExecutor());
        jobLauncher.afterPropertiesSet();

        final JobParameters jobParameters = new JobParametersBuilder().addLong("run", 1L).toJobParameters();
        final JobExecution jobExecution = jobLauncher.run(job, jobParameters);

        // The two failing loans (3 and 8) were skipped, so the batch as a whole finished running...
        assertEquals(BatchStatus.COMPLETED, jobExecution.getStatus());
        // ...but our listener flags the job as failed overall, because at least one loan could not be charged.
        assertEquals(ExitStatus.FAILED.getExitCode(), jobExecution.getExitStatus().getExitCode());

        final List<Long> persistedLoanIds = jdbcTemplate.queryForList("select loan_id from processed_loans order by loan_id", Long.class);
        // All 8 successful loans were committed - including the ones that shared a chunk with a failing loan.
        assertEquals(List.of(1L, 2L, 4L, 5L, 6L, 7L, 9L, 10L), persistedLoanIds);
    }
}
