package io.protop.cli;

import com.google.common.base.Strings;
import io.protop.cli.errors.ExceptionHandler;
import io.protop.core.ProjectCreator;
import io.protop.core.ProjectCreatorImpl;
import io.protop.core.error.ProjectAlreadyCreated;
import io.protop.core.error.ServiceException;
import io.protop.core.logs.Logger;
import io.protop.core.logs.Logs;
import io.protop.core.manifest.Manifest;
import io.protop.core.manifest.revision.Version;
import io.protop.core.storage.StorageService;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.MaskingCallback;
import org.jline.reader.impl.DefaultParser;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Path;

@Command(name = "init",
        description = "Initialize a protop project (generates a protop.json).")
public class Init implements Runnable {

    private static final Logger logger = Logger.getLogger(Init.class);
    private static final Version defaultVersion = new Version("0.1.0");
    private static final String CWD = ".";
    private static final String GITIGNORE_NAME = ".gitignore";

    @ParentCommand
    private ProtopCli protop;

    @Parameters(arity = "0..1",
            description = "Root directory of project",
            defaultValue = ".")
    private Path projectPath;

    @Override
    public void run() {
        Logs.enableIf(protop.isDebugMode());
        new ExceptionHandler().run(() -> {
            if (Manifest.from(projectPath).isPresent()) {
                throw new ProjectAlreadyCreated();
            }

            logger.info("Creating a new project.");

            LineReader reader = LineReaderBuilder.builder()
                    .parser(new DefaultParser())
                    .build();

            Manifest manifest = Manifest.builder()
                    .organization(getOrganization(reader))
                    .name(getName(reader))
                    .version(getVersion(reader))
//                    .include(getPathsToInclude())
//                    .dependencies(DependencyMap.empty())
                    .build();

            createProject(manifest, projectPath.toAbsolutePath());
        });
    }

    private void createProject(Manifest manifest, Path projectPath) {
        StorageService storageService = new StorageService();
        ProjectCreator projectCreator = new ProjectCreatorImpl(storageService);

        try {
            projectCreator.create(manifest, projectPath);

            logger.always("Initialized new project.");
            recommendGitignore();
        } catch (Exception e) {
            logger.always("Failed to create new project.");
            if (!Strings.isNullOrEmpty(e.getMessage()) && (e instanceof ServiceException)) {
                logger.always(e.getMessage());
            }
        }
    }

//    private List<Path> getPathsToInclude() {
//        return ImmutableList.of(Paths.get("."));
//    }

    private String getOrganization(LineReader reader) {
        String prompt = "Organization name (required): ";
        String rightPrompt = "";
        String name = reader.readLine(
                prompt, rightPrompt, (MaskingCallback) null,null);
        if (Strings.isNullOrEmpty(name)) {
            return getOrganization(reader);
        } else {
            return name;
        }
    }

    private String getName(LineReader reader) {
        String prompt = "Project name (required): ";
        String rightPrompt = "";
        String name = reader.readLine(
                prompt, rightPrompt, (MaskingCallback) null,null);
        if (Strings.isNullOrEmpty(name)) {
            return getName(reader);
        } else {
            return name;
        }
    }

    private Version getVersion(LineReader reader) {
        String prompt = String.format("Initial revision (default %s): ", defaultVersion);
        String rightPrompt = "";
        String version = reader.readLine(
                prompt, rightPrompt, (MaskingCallback) null,null);
        if (Strings.isNullOrEmpty(version)) {
            return defaultVersion;
        } else {
            return new Version(version);
        }
    }

    private void recommendGitignore() {
        if (projectPath.resolve(GITIGNORE_NAME).toFile().exists()) {
            logger.always("It is recommended to add \".protop/\" and \".protoprc\" to your .gitignore.");
        }
    }
}
