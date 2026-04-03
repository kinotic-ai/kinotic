-- Create the vm_node table for tracking VmManager nodes
CREATE TABLE IF NOT EXISTS kinotic_vm_node (
    id KEYWORD,
    name KEYWORD,
    hostname KEYWORD,
    status KEYWORD,
    totalCpus INTEGER,
    totalMemoryMb INTEGER,
    totalDiskMb INTEGER,
    allocatedCpus INTEGER,
    allocatedMemoryMb INTEGER,
    allocatedDiskMb INTEGER,
    lastSeen DATE,
    created DATE,
    updated DATE
);

-- Create the workload table for tracking deployed workloads
CREATE TABLE IF NOT EXISTS kinotic_workload (
    id KEYWORD,
    name KEYWORD,
    description TEXT,
    nodeId KEYWORD,
    providerType KEYWORD,
    image KEYWORD,
    vcpus INTEGER,
    memoryMb INTEGER,
    diskSizeMb INTEGER,
    status KEYWORD,
    environment JSON NOT INDEXED,
    portMappings JSON NOT INDEXED,
    created DATE,
    updated DATE
);
