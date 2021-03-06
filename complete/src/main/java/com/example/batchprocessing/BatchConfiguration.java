package com.example.batchprocessing;

import javax.sql.DataSource;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.database.*;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.batch.item.database.orm.JpaNativeQueryProvider;
import org.springframework.batch.item.database.support.AbstractSqlPagingQueryProvider;
import org.springframework.batch.item.database.support.HsqlPagingQueryProvider;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.persistence.EntityManagerFactory;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


// tag::setup[]
@Configuration
@EnableBatchProcessing
public class BatchConfiguration {

	@Autowired
	public JobBuilderFactory jobBuilderFactory;

	@Autowired
	public StepBuilderFactory stepBuilderFactory;

	@Autowired
	private EntityManagerFactory managerFactory;

	@Autowired
	private DataSource dataSource;


	// end::setup[]

	// tag::readerwriterprocessor[]
	@Bean
	public FlatFileItemReader<Person> reader() {
		return new FlatFileItemReaderBuilder<Person>()
			.name("personItemReader")
			.resource(new ClassPathResource("sample-data.csv"))
			.delimited()
			.names(new String[]{"firstName", "lastName"})
			.fieldSetMapper(new BeanWrapperFieldSetMapper<Person>() {{
				setTargetType(Person.class);
			}})
			.build();
	}

//	@Bean
//	public JpaPagingItemReader<Person> jpaReader(){
//		return new JpaPagingItemReaderBuilder<Person>()
//				.entityManagerFactory(managerFactory)
//				.queryString("select i.msg_id,i.msg_content,j.obj_mobile from jqy.jqy_t_msg_inf i\n" +
//						"left join jqy.jqy_t_msg_sendobj j\n" +
//						"on i.msg_id= j.msg_id")
//				.name("msgSendReaders")
//				.build();
//	}

	@Bean(destroyMethod="")
	public ItemReader<? extends Person> jpaReader() {
		JpaPagingItemReader<Person> reader = new JpaPagingItemReader<Person>();
		String sqlQuery = "SELECT first_name, last_name from FZ_BACKUP.PERSON";
		try {
			JpaNativeQueryProvider<Person> queryProvider = new JpaNativeQueryProvider<Person>();
			queryProvider.setSqlQuery(sqlQuery);
			queryProvider.setEntityClass(Person.class);
			queryProvider.afterPropertiesSet();
			reader.setEntityManagerFactory(managerFactory);
			reader.setPageSize(3);
			reader.setQueryProvider(queryProvider);
//			reader.setParameterValues(Collections.<String, Object>singletonMap("limit", 1));
			reader.afterPropertiesSet();
			reader.setSaveState(true);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return reader;
	}

	@Bean
	public JdbcPagingItemReader<Person> jdbcReaders(){

		Map<String, Order> sortKeys = new HashMap<>(1);
		sortKeys.put("first_name", Order.DESCENDING);

		AbstractSqlPagingQueryProvider provider = new HsqlPagingQueryProvider();
		provider.setSelectClause("SELECT first_name, last_name");
		provider.setFromClause("from FZ_BACKUP.PERSON");
		provider.setWhereClause("where first_name <> '1'");
		provider.setSortKeys(sortKeys);
		return new JdbcPagingItemReaderBuilder<Person>()
				.name("personItemReader")
				.dataSource(dataSource)
				.queryProvider(provider)
				.build();
	}

	@Bean
	public PersonItemProcessor processor() {
		return new PersonItemProcessor();
	}

	@Bean
	public JdbcBatchItemWriter<Person> writer(DataSource dataSource) {
		return new JdbcBatchItemWriterBuilder<Person>()
			.itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
			.sql("INSERT INTO fz_backup.people (first_name, last_name) VALUES (:firstName, :lastName)")
			.dataSource(dataSource)
			.build();
	}
	// end::readerwriterprocessor[]

	// tag::jobstep[]
	@Bean
	public Job importUserJob(JobCompletionNotificationListener listener, Step step1) {
		return jobBuilderFactory.get("importUserJob")
			.incrementer(new RunIdIncrementer())
			.listener(listener)
			.flow(step1)
			.end()
			.build();
	}

	@Bean
	public Step step1(JdbcBatchItemWriter<Person> writer) {
		return stepBuilderFactory.get("step1")
			.<Person, Person> chunk(10)
			.reader(jpaReader())
			.processor(processor())
			.writer(writer)
			.build();
	}
	// end::jobstep[]
}
