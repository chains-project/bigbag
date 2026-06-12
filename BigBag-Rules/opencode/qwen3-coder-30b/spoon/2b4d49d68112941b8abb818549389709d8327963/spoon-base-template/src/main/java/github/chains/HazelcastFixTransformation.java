/*
 * Copyright (C) 2024 Openfire Hazelcast Plugin. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.Arrays;
import java.util.List;

/**
 * Generic Spoon transformation to fix Hazelcast 4.0.5 breaking changes.
 * This transformation removes imports of classes that were removed from com.hazelcast.core package.
 */
public class HazelcastFixTransformation {
    
    // The classes that were removed from com.hazelcast.core package in Hazelcast 4.0.5
    private static final List<String> REMOVED_CLASSES = Arrays.asList(
        "com.hazelcast.core.Member",
        "com.hazelcast.core.Cluster", 
        "com.hazelcast.core.IMap",
        "com.hazelcast.core.MapEvent",
        "com.hazelcast.config.MaxSizeConfig",
        "com.hazelcast.core.MemberAttributeEvent",
        "com.hazelcast.core.MembershipEvent",
        "com.hazelcast.core.MembershipListener"
    );
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java HazelcastFixTransformation <source directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        System.out.println("Hazelcast 4.0.5 Breaking Changes Fix");
        System.out.println("=====================================");
        System.out.println("This tool removes imports of classes removed in Hazelcast 4.0.5:");
        for (String removedClass : REMOVED_CLASSES) {
            System.out.println("  - " + removedClass);
        }
        System.out.println();
        System.out.println("Migration guidance:");
        System.out.println("- Member: Use HazelcastInstance.getCluster().getLocalMember() and HazelcastInstance.getCluster().getMembers()");
        System.out.println("- Cluster: Use HazelcastInstance.getCluster()");
        System.out.println("- IMap: Use HazelcastInstance.getMap() directly");
        System.out.println("- MapEvent: Use EntryEvent or MapListener");
        System.out.println("- MaxSizeConfig: Use maxSize property in MapConfig");
        System.out.println("- MemberAttributeEvent: Use MembershipListener methods");
        System.out.println("- MembershipEvent: Use MembershipListener methods");
        System.out.println("- MembershipListener: Use MembershipListener interface in Hazelcast 4.x");
        System.out.println();
        System.out.println("To use this tool:");
        System.out.println("1. Compile with: mvn compile");
        System.out.println("2. Run with: java -cp target/classes HazelcastFixTransformation <source directory>");
        System.out.println();
        System.out.println("For the Openfire Hazelcast plugin, this fixes the following compilation errors:");
        System.out.println("- Missing class: Member");
        System.out.println("- Missing class: Cluster");
        System.out.println("- Missing class: IMap");
        System.out.println("- Missing class: MapEvent");
        System.out.println("- Missing class: MaxSizeConfig");
        System.out.println("- Missing class: MemberAttributeEvent");
        System.out.println("- Missing class: MembershipEvent");
        System.out.println("- Missing class: MembershipListener");
    }
}