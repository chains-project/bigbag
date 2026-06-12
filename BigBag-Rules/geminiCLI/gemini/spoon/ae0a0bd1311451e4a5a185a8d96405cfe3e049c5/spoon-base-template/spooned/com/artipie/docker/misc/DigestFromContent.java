/* The MIT License (MIT) Copyright (c) 2020-2022 artipie.com
https://github.com/artipie/docker-adapter/LICENSE.txt
 */
package com.artipie.docker.misc;
import com.artipie.docker.Digest;
import java.util.concurrent.CompletionStage;
import com.artipie.asto.Content;
import com.artipie.asto.ext.ContentDigest;
import com.artipie.asto.ext.Digests;
/**
 * Digest from content.
 *
 * @since 0.2
 */
public final class DigestFromContent {
    /**
     * Content.
     */
    private final Content content;

    /**
     * Ctor.
     *
     * @param content
     * 		Content publisher
     */
    public DigestFromContent(final Content content) {
        this.content = content;
    }

    /**
     * Calculates digest from content.
     *
     * @return CompletionStage from digest
     */
    public CompletionStage<Digest> digest() {
        return new ContentDigest(this.content, Digests.SHA256).hex().thenApply(Digest.Sha256::new);
    }
}
