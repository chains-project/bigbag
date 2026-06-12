/* The MIT License (MIT) Copyright (c) 2020-2022 artipie.com
https://github.com/artipie/docker-adapter/LICENSE.txt
 */
package com.artipie.docker.asto;
import com.artipie.docker.Digest;
import java.util.concurrent.CompletionStage;
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
/**
 * Source of blob that could be saved to {@link Storage} at desired location.
 *
 * @since 0.12
 */
public interface BlobSource {
    /**
     * Blob digest.
     *
     * @return Digest.
     */
    Digest digest();

    /**
     * Save blob to storage.
     *
     * @param storage
     * 		Storage.
     * @param key
     * 		Destination for blob content.
     * @return Completion of save operation.
     */
    CompletionStage<Void> saveTo(Storage storage, Key key);
}
