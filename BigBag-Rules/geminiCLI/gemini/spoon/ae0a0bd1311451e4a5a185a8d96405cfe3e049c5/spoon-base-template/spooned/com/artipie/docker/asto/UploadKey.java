/* The MIT License (MIT) Copyright (c) 2020-2022 artipie.com
https://github.com/artipie/docker-adapter/LICENSE.txt
 */
package com.artipie.docker.asto;
import com.artipie.docker.RepoName;
import com.artipie.asto.Key;
/**
 * Key of blob upload root.
 *
 * @since 0.3
 */
final class UploadKey extends Key.Wrap {
    /**
     * Ctor.
     *
     * @param name
     * 		Repository name.
     * @param uuid
     * 		Upload UUID.
     */
    UploadKey(final RepoName name, final String uuid) {
        super(new Key.From("repositories", name.value(), "_uploads", uuid));
    }
}
