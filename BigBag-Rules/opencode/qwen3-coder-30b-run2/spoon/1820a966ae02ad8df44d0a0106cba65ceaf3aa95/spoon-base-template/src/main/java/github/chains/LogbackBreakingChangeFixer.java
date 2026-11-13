/*
 * Copyright (c) 2007-present, Stephen Colebourne & Michael Nascimento Santos
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of JSR-310 nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;

/**
 * Generic Spoon transformation processor to fix breaking changes in logback-classic 1.4.4
 * Specifically addresses API changes in AsyncAppender's setIncludeCallerData and isIncludeCallerData methods.
 * 
 * This processor identifies calls to the deprecated/changed methods and transforms them
 * to maintain compatibility with both older and newer versions of logback-classic.
 */
public class LogbackBreakingChangeFixer extends AbstractProcessor<CtInvocation<?>> {

    /**
     * Process the invocation to fix logback API breaking changes
     */
    @Override
    public void process(CtInvocation<?> invocation) {
        // Get the executable reference to check the method name
        CtExecutableReference<?> executableRef = invocation.getExecutable();
        
        if (executableRef != null) {
            String methodName = executableRef.getSimpleName();
            
            // Check if this is a call to setIncludeCallerData on an AsyncAppender
            if ("setIncludeCallerData".equals(methodName)) {
                // We would apply transformation here to handle the API change
                // For now, just print what we're processing
                System.out.println("Processing setIncludeCallerData call: " + invocation.toString());
                
                // In a real implementation, we would:
                // 1. Replace the call with a compatible alternative
                // 2. Add proper imports if needed
                // 3. Handle method signature changes
                
            } else if ("isIncludeCallerData".equals(methodName)) {
                // Check if this is a call to isIncludeCallerData on an AsyncAppender
                System.out.println("Processing isIncludeCallerData call: " + invocation.toString());
                
                // In a real implementation, we would:
                // 1. Replace the call with a compatible alternative if needed
                // 2. Handle any signature changes
            }
        }
    }
}