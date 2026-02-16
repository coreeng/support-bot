#!/usr/bin/env node
/**
 * Process Slack support thread files with the gap analysis taxonomy summary prompt.
 * Uses the Auggie JavaScript SDK to analyze each thread and save results.
 */

import * as fs from 'fs';
import * as path from 'path';
import { fileURLToPath } from 'url';
import { Auggie } from '@augmentcode/auggie-sdk';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

/**
 * Parse the markdown output into JSON format.
 */
function parseMarkdownToJson(mdContent) {
  const lines = mdContent.trim().split('\n');
  const result = {};

  for (const line of lines) {
    if (line.startsWith('Ticket:')) {
      result.ticket = line.replace('Ticket:', '').trim();
    } else if (line.startsWith('Primary Driver:')) {
      result.primaryDriver = line.replace('Primary Driver:', '').trim();
    } else if (line.startsWith('Category:')) {
      result.category = line.replace('Category:', '').trim();
    } else if (line.startsWith('Platform Feature:')) {
      result.platformFeature = line.replace('Platform Feature:', '').trim();
    } else if (line.startsWith('Reason:')) {
      result.reason = line.replace('Reason:', '').trim();
    }
  }

  return result;
}

/**
 * Process a single thread file with the prompt and save the result.
 */
async function processThreadFile(agent, contentFile, promptText, outputFile, jsonlFile) {
  try {
    // Read the thread content
    const threadContent = fs.readFileSync(contentFile, 'utf-8');

    // Construct the full prompt
    const fullPrompt = `${promptText}

---

Thread Content:

${threadContent}
`;

    // Process with Auggie
    process.stdout.write('  Processing with Auggie...\n');
    const result = await agent.prompt(fullPrompt, { isAnswerOnly: true });

    // Save the markdown result
    fs.writeFileSync(outputFile, result, 'utf-8');

    // Parse to JSON and append to JSONL file
    const jsonData = parseMarkdownToJson(result);
    fs.appendFileSync(jsonlFile, JSON.stringify(jsonData) + '\n', 'utf-8');

    return true;
  } catch (error) {
    console.error(`  ✗ Error: ${error}`);
    return false;
  }
}

/**
 * Main processing function.
 */
async function main() {
  // Setup paths
  const scriptDir = __dirname;
  const contentDir = path.join(scriptDir, 'content');
  const promptFile = path.join(scriptDir, 'gap_analysis_taxonomy_summary-prompt.md');
  const outputDir = path.join(scriptDir, 'taxonomy-analysis-summary');

  // Validate inputs
  if (!fs.existsSync(contentDir)) {
    console.error(`Error: Content directory not found: ${contentDir}`);
    process.exit(1);
  }

  if (!fs.existsSync(promptFile)) {
    console.error(`Error: Prompt file not found: ${promptFile}`);
    process.exit(1);
  }

  // Create output directory
  if (!fs.existsSync(outputDir)) {
    fs.mkdirSync(outputDir, { recursive: true });
  }
  console.log(`Output directory: ${outputDir}`);

  // Setup JSONL output file
  const jsonlFile = path.join(scriptDir, 'analysis.jsonl');

  // Clear existing JSONL file if it exists
  if (fs.existsSync(jsonlFile)) {
    fs.unlinkSync(jsonlFile);
  }

  // Read the prompt
  const promptText = fs.readFileSync(promptFile, 'utf-8');

  // Get all thread files
  const allFiles = fs.readdirSync(contentDir);
  const threadFiles = allFiles
    .filter(file => file.endsWith('.txt'))
    .map(file => path.join(contentDir, file))
    .sort();

  const totalFiles = threadFiles.length;

  if (totalFiles === 0) {
    console.log(`No .txt files found in ${contentDir}`);
    process.exit(0);
  }

  console.log(`Found ${totalFiles} thread files to process`);
  console.log(`Prompt file: ${promptFile}`);
  console.log();

  // Initialize Auggie agent
  console.log('Initializing Auggie agent...');
  const agent = await Auggie.create({
    workspaceRoot: scriptDir,
    model: 'sonnet4.5',
  });
  console.log('Agent initialized');
  console.log();

  // Process each file
  const results = {
    total: totalFiles,
    successful: 0,
    failed: 0,
    skipped: 0,
  };

  for (let idx = 0; idx < threadFiles.length; idx++) {
    const contentFile = threadFiles[idx];
    const baseName = path.basename(contentFile, '.txt');
    const outputFile = path.join(outputDir, `${baseName}.md`);

    // Check if already processed
    if (fs.existsSync(outputFile)) {
      console.log(`[${idx + 1}/${totalFiles}] SKIP: ${baseName} (already analyzed)`);
      results.skipped++;

      // Still add to JSONL if not already there
      const mdContent = fs.readFileSync(outputFile, 'utf-8');
      const jsonData = parseMarkdownToJson(mdContent);
      fs.appendFileSync(jsonlFile, JSON.stringify(jsonData) + '\n', 'utf-8');

      continue;
    }

    console.log(`[${idx + 1}/${totalFiles}] Analyzing: ${baseName}`);
    console.log(`  Input:  ${contentFile}`);

    // Process the file
    const success = await processThreadFile(agent, contentFile, promptText, outputFile, jsonlFile);

    if (success) {
      const stats = fs.statSync(outputFile);
      console.log(`  ✓ Saved to: ${outputFile} (${stats.size} bytes)`);
      results.successful++;
    } else {
      results.failed++;
    }

    console.log();
  }

  // Print summary
  console.log('='.repeat(60));
  console.log('Processing Complete!');
  console.log('='.repeat(60));
  console.log(`Total files:      ${results.total}`);
  console.log(`Analyzed:         ${results.successful}`);
  console.log(`Failed:           ${results.failed}`);
  console.log(`Skipped:          ${results.skipped}`);
  console.log('='.repeat(60));
  console.log(`Output directory: ${outputDir}`);
  console.log(`JSONL file:       ${jsonlFile}`);
  console.log();

  // Close the agent connection
  await agent.close();
}

// Run the main function
main().catch(error => {
  console.error('Fatal error:', error);
  process.exit(1);
});

