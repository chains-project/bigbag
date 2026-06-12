/* The MIT License (MIT) Copyright (c) 2020-2022 artipie.com
https://github.com/artipie/docker-adapter/LICENSE.txt
 */
package com.artipie.docker;
import java.util.concurrent.CompletionStage;
import com.artipie.asto.Content;
/**
 * Blob stored in repository.
 *
 * @since 0.2
 */
public interface Blob {
    /**
     * Blob digest.
     *
     * @return Digest.
     */
    Digest digest();

    /**
     * Read blob size.
     *
     * @return Size of blob in bytes.
     */
    CompletionStage<Long> size();

    /**
     * Read blob content.
     *
     * @return Content.
     */
    CompletionStage<Content> content();
}
