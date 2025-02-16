package com.arlei.codeticket.controller;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/importacao")
public class ImportacaoController {

    private JobLauncher jobLauncher;
    private Job job;

    public ImportacaoController(JobLauncher jobLauncher, Job job) {
        this.jobLauncher = jobLauncher;
        this.job = job;
    }

    @GetMapping
    public void importar() {
        try {
            if (job == null) {
                throw new Exception("Job não configurado");
            }
            JobParameters jobParameters = new JobParameters();
            jobLauncher.run(job, jobParameters);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}