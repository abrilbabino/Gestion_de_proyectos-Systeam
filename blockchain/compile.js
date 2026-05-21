const solc = require('solc');
const fs = require('fs');
const path = require('path');

const contractsDir = path.join(__dirname, 'contracts');
const buildDir = path.join(__dirname, 'compiled');

if (!fs.existsSync(buildDir)) {
    fs.mkdirSync(buildDir, { recursive: true });
}

// Read all contract files
const files = fs.readdirSync(contractsDir).filter(f => f.endsWith('.sol'));

const sources = {};
for (const file of files) {
    const content = fs.readFileSync(path.join(contractsDir, file), 'utf8');
    sources[file] = { content };
}

// Read OpenZeppelin imports
function findImports(importPath) {
    if (importPath.startsWith('@openzeppelin')) {
        const fullPath = path.join(__dirname, 'node_modules', importPath);
        if (fs.existsSync(fullPath)) {
            return { contents: fs.readFileSync(fullPath, 'utf8') };
        }
    }
    // Local imports
    const localPath = path.join(contractsDir, importPath);
    if (fs.existsSync(localPath)) {
        return { contents: fs.readFileSync(localPath, 'utf8') };
    }
    return { error: 'File not found: ' + importPath };
}

const input = {
    language: 'Solidity',
    sources,
    settings: {
        outputSelection: {
            '*': {
                '*': ['abi', 'evm.bytecode.object']
            }
        },
        evmVersion: 'paris',
        optimizer: { enabled: true, runs: 200 }
    }
};

const output = JSON.parse(
    solc.compile(JSON.stringify(input), { import: findImports })
);

if (output.errors) {
    for (const err of output.errors) {
        if (err.severity === 'error') {
            console.error('ERROR:', err.formattedMessage);
        }
    }
}

// Save each contract
for (const [file, contracts] of Object.entries(output.contracts || {})) {
    for (const [contractName, data] of Object.entries(contracts)) {
        const bytecode = data.evm.bytecode.object;
        const abi = data.abi;

        const outFile = path.join(buildDir, `${contractName}.json`);
        fs.writeFileSync(outFile, JSON.stringify({
            contractName,
            bytecode,
            abi,
            sourceFile: file
        }, null, 2));

        console.log(`✓ ${contractName} -> compiled/${contractName}.json`);
        console.log(`  Bytecode: ${bytecode.substring(0, 60)}...`);
    }
}

console.log('\nDone! All contracts compiled.');
