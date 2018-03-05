/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.repository.raw;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.raw.internal.RawFormat;
import org.sonatype.nexus.repository.rest.UploadDefinitionExtension;
import org.sonatype.nexus.repository.security.ContentPermissionChecker;
import org.sonatype.nexus.repository.security.VariableResolverAdapter;
import org.sonatype.nexus.repository.storage.StorageFacet;
import org.sonatype.nexus.repository.transaction.TransactionalStoreBlob;
import org.sonatype.nexus.repository.upload.UploadHandlerSupport;
import org.sonatype.nexus.repository.upload.AssetUpload;
import org.sonatype.nexus.repository.upload.ComponentUpload;
import org.sonatype.nexus.repository.upload.UploadDefinition;
import org.sonatype.nexus.repository.upload.UploadFieldDefinition;
import org.sonatype.nexus.repository.upload.UploadFieldDefinition.Type;
import org.sonatype.nexus.repository.upload.UploadRegexMap;
import org.sonatype.nexus.repository.upload.UploadResponse;
import org.sonatype.nexus.repository.view.Content;

import com.google.common.collect.Lists;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;

/**
 * Support for uploading components via UI & API
 *
 * @since 3.7
 */
@Named(RawFormat.NAME)
@Singleton
public class RawUploadHandler
    extends UploadHandlerSupport
{
  private static final String FILENAME = "filename";

  private static final String DIRECTORY = "directory";

  private static final String DIRECTORY_HELP_TEXT = "Destination for uploaded files (e.g. /path/to/files/)";

  private UploadDefinition definition;

  private final ContentPermissionChecker contentPermissionChecker;

  private final VariableResolverAdapter variableResolverAdapter;

  @Inject
  public RawUploadHandler(final ContentPermissionChecker contentPermissionChecker,
                          @Named("simple") final VariableResolverAdapter variableResolverAdapter,
                          final Set<UploadDefinitionExtension> uploadDefinitionExtensions)
  {
    super(uploadDefinitionExtensions);
    this.contentPermissionChecker = contentPermissionChecker;
    this.variableResolverAdapter = variableResolverAdapter;
  }

  @Override
  public UploadResponse handle(final Repository repository, final ComponentUpload upload) throws IOException {
    RawContentFacet facet = repository.facet(RawContentFacet.class);

    String basePath = normalizeBasePath(upload.getFields().get(DIRECTORY));

    return TransactionalStoreBlob.operation.withDb(repository.facet(StorageFacet.class).txSupplier())
        .throwing(IOException.class).call(() -> {

          //Data holders for populating the UploadResponse
          Content responseContent = null;
          List<String> assetPaths = Lists.newArrayList();

          for (AssetUpload asset : upload.getAssetUploads()) {
            String path = basePath + normalizeFilename(asset.getFields().get(FILENAME));

            ensurePermitted(repository.getName(), RawFormat.NAME, path, emptyMap());

            Content content = facet.put(path, asset.getPayload());

            //We only need to set this one time, it provides the component id which
            //is the same for all assets upload for a component
            if (responseContent == null) {
              responseContent = content;
            }
            assetPaths.add(path);
          }

          return new UploadResponse(responseContent, assetPaths);
        });
  }

  private String normalizeBasePath(final String basePath) {
    String result = normalize(basePath);

    if (!result.endsWith("/")) {
      result += "/";
    }

    return result;
  }

  private String normalizeFilename(final String filename) {
    String result = normalize(filename);

    if (result.endsWith("/")) {
      result = result.substring(0, result.length() - 1);
    }

    return result;
  }

  private String normalize(final String string) {
    String result = string.trim().replaceAll("/+", "/");

    if (result.startsWith("/")) {
      return result.substring(1);
    }
    return result;
  }

  @Override
  public UploadDefinition getDefinition() {
    if (definition == null) {
      definition = getDefinition(RawFormat.NAME, true,
          singletonList(new UploadFieldDefinition(DIRECTORY, DIRECTORY_HELP_TEXT, false, Type.STRING)),
          singletonList(new UploadFieldDefinition(FILENAME, false, Type.STRING)), new UploadRegexMap("(.*)", FILENAME));
    }
    return definition;
  }

  @Override
  public VariableResolverAdapter getVariableResolverAdapter() {
    return variableResolverAdapter;
  }

  @Override
  public ContentPermissionChecker contentPermissionChecker() {
    return contentPermissionChecker;
  }
}
