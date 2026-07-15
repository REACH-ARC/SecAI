package com.secai;

import com.secai.cli.SecAiCommand;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

import org.springframework.boot.ExitCodeGenerator;

@SpringBootApplication
public class SecaiApplication implements CommandLineRunner, ExitCodeGenerator {

    private final SecAiCommand secAiCommand;
    private final IFactory factory;
    private int exitCode;

    public SecaiApplication(SecAiCommand secAiCommand, IFactory factory) {
        this.secAiCommand = secAiCommand;
        this.factory = factory;
    }

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(SecaiApplication.class, args)));
    }

    @Override
    public void run(String... args) {
        exitCode = new CommandLine(secAiCommand, factory).execute(args);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
