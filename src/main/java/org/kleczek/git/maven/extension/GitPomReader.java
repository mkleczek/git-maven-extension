package org.kleczek.git.maven.extension;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.model.Model;
import org.apache.maven.model.building.DefaultModelProcessor;
import org.apache.maven.model.building.FileModelSource;
import org.apache.maven.model.building.ModelProcessor;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;

@Component(role = ModelProcessor.class)
public class GitPomReader extends DefaultModelProcessor
{

    @Requirement
    Logger logger;

    @Override
    public Model read(File input,
                      Map<String, ?> options)
            throws IOException
    {
        return processModel(super.read(input, options), getFileFromOptions(options));
    }

    @Override
    public Model read(InputStream input,
                      Map<String, ?> options)
            throws IOException
    {
        return processModel(super.read(input, options), getFileFromOptions(options));
    }

    @Override
    public Model read(Reader input,
                      Map<String, ?> options)
            throws IOException
    {
        return processModel(super.read(input, options), getFileFromOptions(options));
    }

    private Model processModel(final Model source,
                               Optional<File> pomFile)
    {
        try
        {
            if (!pomFile.isPresent())
            {
                logger.debug("No pom file for {}:{}", source.getGroupId(), source.getArtifactId());
                return source;
            }

            final FileRepositoryBuilder repoBuilder = new FileRepositoryBuilder().readEnvironment()
                    .findGitDir(pomFile.get().getParentFile());
            if (repoBuilder.getGitDir() == null)
            {
                logger.debug("Git repository not found for {}:{}", source.getGroupId(), source.getArtifactId());
                return source;
            }
            try (final Repository repo = repoBuilder.build())
            {
                final ObjectId head = repo.resolve(Constants.HEAD);
                final Git git = new Git(repo);

                if (head == null)
                {
                    logger.debug("Cannot parse HEAD for {}:{}", source.getGroupId(), source.getArtifactId());
                    return source;
                }

                final String version = version(repo.getRefDatabase()
                        .getRefsByPrefix(Constants.R_TAGS)
                        .stream()
                        .filter(ref -> head.equals(ref.getObjectId()))
                        .map(Ref::getName)
                        .map(name -> name.substring("refs/tags/".length(), name.length())), head, source);

                source.setVersion(version);

                // TODO fill in Git properties

                return source;
            }

        }
        catch (IOException e)
        {
            logger.debug("Failed to retrieve Git metadata", e);
            return source;
        }
    }

    private String version(final Stream<String> tags,
                           final ObjectId head,
                           Model source)
    {
        final Set<String> releasePrefixes = new HashSet<>(Arrays.asList("release/", "releases/", "release-"));
        final Set<String> tagSet = tags.collect(Collectors.toSet());
        return tagSet.stream().flatMap(tag -> {
            return releasePrefixes.stream()
                    .filter(prefix -> tag.startsWith(prefix))
                    .map(prefix -> tag.substring(prefix.length(), tag.length()));
        }).findFirst().orElse(tagSet.stream().findFirst().orElse(head.getName()));
    }

    private Optional<File> getFileFromOptions(Map<String, ?> options)
    {
        if (options.get(SOURCE) instanceof FileModelSource)
        {
            return Optional.of(((FileModelSource) options.get(SOURCE)).getFile());
        }

        return Optional.empty();
    }

}
