package com.arlei.codeticket.job;


import com.arlei.codeticket.mapper.ImportacaoMapper;
import com.arlei.codeticket.model.Importacao;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;
import javax.sql.DataSource;
import java.io.File;


@Configuration
public class ImportacaoJobConfiguration {

    // Controla as transações do chunck
    @Autowired
    private PlatformTransactionManager transactionManager;

    // criar job para ler csv e salvar no banco
    // O bean para ser gerenciado pelo spring
    // O método que retorna o job é anotado com @Bean
    // O método recebe um Step como parâmetro
    // O método retorna um JobBuilder que recebe o nome do job e o repositório de jobs
    // O JobBuilder tem um método start que recebe o Step passado como parâmetro
    // O JobBuilder tem um método incrementer que recebe um RunIdIncrementer que controla os logs do job
    @Bean
    public Job job(Step passoInicial, JobRepository jobRepository) {
        return new JobBuilder("geracao-tickets", jobRepository)
                .start(passoInicial)
                .next(moverArquivosStep(jobRepository))
                .incrementer(new RunIdIncrementer())
                .build();
    }


    @Bean
    public Step passoInicial(ItemReader<Importacao> reader,
                             ItemProcessor<Importacao,Importacao> processor,
                             ItemWriter<Importacao> writer,
                             JobRepository jobRepository) {
        return new StepBuilder("passo-inicial", jobRepository)
                .<Importacao, Importacao>chunk(200, transactionManager)
                .reader(reader)
                .processor(processor())
                .writer(writer)
                .build();
    }

    @Bean
    public ItemReader<Importacao> reader() {
        return new FlatFileItemReaderBuilder<Importacao>()
                .name("leitura-csv")
                .resource(new FileSystemResource("files/dados.csv"))
                .comments("--")
                .delimited()
                .delimiter(";")
                .names("cpf", "cliente", "nascimento", "evento", "data", "tipoIngresso", "valor")
                //.targetType(Importacao.class)
                .fieldSetMapper(new ImportacaoMapper())
                .build();
    }

    // lembra que no aplication properties tem que colocar o jdbc url
    @Bean
    public ItemWriter<Importacao> writer(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<Importacao>()
                .dataSource(dataSource)
                .sql(
                        "INSERT INTO importacao (cpf, cliente, nascimento, evento, data, tipo_ingresso, valor, hora_importacao, taxa_adm) VALUES" +
                                " (:cpf, :cliente, :nascimento ,:evento, :data, :tipoIngresso, :valor, :horaImportacao, :taxaAdm)"
                )
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .build();
    }

    @Bean
    public ItemProcessor<Importacao,Importacao> processor(){
        return new ImportacaoProcessor();
    }

    // Usar mais um step mas com tasklet, lembrando que tbm posso ao JOB um Flow ou SimpleFlow que pode ter N Steps e executar em paralelo
    // como tenho na minha outra conta do GIT ArleiPacanaro
    // Tasklet é uma interface que tem um método execute que recebe um StepContribution e um ChunkContext
    @Bean
    public Tasklet moverArquivosTasklet() {
        return (contribution, chunkContext) -> {
            File pastaOrigem = new File("files");
            File pastaDestino = new File("imported-files");

            if (!pastaDestino.exists()) {
                pastaDestino.mkdirs();
            }

            File[] arquivos = pastaOrigem.listFiles((dir, name) -> name.endsWith(".csv"));

            if (arquivos != null) {
                for (File arquivo : arquivos) {
                    File arquivoDestino = new File(pastaDestino, arquivo.getName());
                    if (arquivo.renameTo(arquivoDestino)) {
                        System.out.println("Arquivo movido: " + arquivo.getName());
                    } else {
                        throw new RuntimeException("Não foi possível mover o arquivo: " + arquivo.getName());
                    }
                }
            }
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Step moverArquivosStep(JobRepository jobRepository) {
        return new StepBuilder("mover-arquivo", jobRepository)
                .tasklet(moverArquivosTasklet(), transactionManager)
                .build();
    }
}

/* deixar uma exemplo de atualização...

A importância de definir um ItemWriter no Spring Batch para escrita de dados no banco;

A necessidade de configurar um DataSource para conectar o ItemWriter ao banco de dados;

Como criar a classe DataSourceConfig para centralizar as configurações de DataSource e TransactionManager;

A configurar e utilizar um FieldSetMapper para personalizar a criação do objeto Importacao;

A executar a aplicação e testar o processamento em lote, identificando e corrigindo bugs nas configurações.

@Configuration
public class ProcessamentoClientesJobConfiguration {

    // outras configuracoes omitidas…

    @Bean
    public ItemReader<Cliente> clienteReader() {
        return new FlatFileItemReaderBuilder<Cliente>()
                .name("leituraClientes")
                .resource(new FileSystemResource("clientes.csv"))
                .delimited()
                .delimiter(";")
                .names("id", "nome", "email", "endereco")
                .fieldSetMapper(new ClienteMapper())
                .build();
    }

    @Bean
    public ItemWriter<Cliente> clienteWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<Cliente>()
                .dataSource(dataSource)
                .sql("UPDATE cliente SET endereco = :endereco WHERE id = :id")
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .build();
    }
}
 */


