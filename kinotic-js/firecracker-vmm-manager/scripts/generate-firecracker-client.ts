import { createClient } from '@hey-api/openapi-ts';

const FIRECRACKER_OPENAPI_URL = 'https://raw.githubusercontent.com/firecracker-microvm/firecracker/main/src/firecracker/swagger/firecracker.yaml';
const OUTPUT_DIR = 'src/internal/api/firecracker/generated';

await createClient({
	input: FIRECRACKER_OPENAPI_URL,
	output: OUTPUT_DIR,
	plugins: ['@hey-api/client-fetch'],
});

console.log(`Firecracker API client generated successfully in ${OUTPUT_DIR}`);
