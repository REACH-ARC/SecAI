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
        SpringApplication app = new SpringApplication(SecaiApplication.class);
        app.setBannerMode(org.springframework.boot.Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        System.exit(SpringApplication.exit(app.run(args)));
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
