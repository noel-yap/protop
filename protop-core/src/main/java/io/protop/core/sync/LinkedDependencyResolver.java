package io.protop.core.sync;

import io.protop.core.manifest.ProjectCoordinate;
import io.protop.core.error.ServiceException;
import io.protop.core.link.LinkedProjectsMap;
import io.protop.core.logs.Logger;
import io.protop.version.Version;
import io.reactivex.Single;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class LinkedDependencyResolver implements DependencyResolver {

    private static final Logger logger = Logger.getLogger(LinkedDependencyResolver.class);

    @Override
    public String getShortDescription() {
        return "linked projects";
    }

    @Override
    public Single<Map<ProjectCoordinate, Version>> resolve(
            Path dependencyDir, Map<ProjectCoordinate, Version> unresolvedDependencies) {
        return Single.fromCallable(() -> {
            LinkedProjectsMap resolvable = LinkedProjectsMap.load()
                    .blockingGet();

            Set<ProjectCoordinate> resolved = new HashSet<>();

            unresolvedDependencies.forEach((name, version) -> {
                if (resolvable.getProjects().containsKey(name)) {
                    try {
                        resolve(dependencyDir, name, resolvable.getProjects().get(name));
                        resolved.add(name);
                    } catch (IOException e) {
                        throw new ServiceException("Unexpectedly failed to resolve linked dependencies.", e);
                    }
                }
            });

            resolved.forEach(unresolvedDependencies::remove);

            return unresolvedDependencies;
        });
    }

    private void resolve(Path dependencyDir, ProjectCoordinate name, Path src) throws IOException {
        Path org = dependencyDir.resolve(name.getOrganizationId());

        if (!Files.isDirectory(org)) {
            Files.deleteIfExists(org);
            Files.createDirectory(org);
        }

        Path project = org.resolve(name.getProjectId());
        if (Files.exists(project)) {
            if (Files.isSymbolicLink(project)) {
                return;
            } else {
                Files.delete(project);
            }
        }

        Files.createSymbolicLink(project, src);
    }
}