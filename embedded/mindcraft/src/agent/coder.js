import { writeFile, mkdirSync, readFileSync } from 'fs';
import path from 'path';
import { makeCompartment, lockdown } from './library/lockdown.js';
import * as skills from './library/skills.js';
import * as world from './library/world.js';
import { Vec3 } from 'vec3';
import { ESLint } from 'eslint';
import { getBotSubPath, getRuntimeBotAssetPath } from '../utils/aiplayers_paths.js';

export class Coder {
    constructor(agent) {
        this.agent = agent;
        this.file_counter = 0;
        this.fp = getBotSubPath(agent.name, 'action-code');
        this.code_template = readFileSync(getRuntimeBotAssetPath('execTemplate.js'), 'utf8');
        this.code_lint_template = readFileSync(getRuntimeBotAssetPath('lintTemplate.js'), 'utf8');
        mkdirSync(this.fp, { recursive: true });
    }

    async generateCode(agent_history) {
        this.agent.bot.modes.pause('unstuck');
        lockdown();
        let messages = agent_history.getHistory();
        messages.push({ role: 'system', content: 'Code generation started. Write code in codeblock in your response:' });

        const MAX_ATTEMPTS = 5;
        const MAX_NO_CODE = 3;

        let code = null;
        let no_code_failures = 0;
        for (let i = 0; i < MAX_ATTEMPTS; i++) {
            if (this.agent.bot.interrupt_code)
                return null;
            const messages_copy = JSON.parse(JSON.stringify(messages));
            let res = await this.agent.prompter.promptCoding(messages_copy);
            if (this.agent.bot.interrupt_code)
                return null;
            let contains_code = res.indexOf('```') !== -1;
            if (!contains_code) {
                if (res.indexOf('!newAction') !== -1) {
                    messages.push({
                        role: 'assistant',
                        content: res.substring(0, res.indexOf('!newAction'))
                    });
                    continue;
                }

                if (no_code_failures >= MAX_NO_CODE) {
                    console.warn('Action failed, agent would not write code.');
                    return 'Action failed, agent would not write code.';
                }
                messages.push({
                    role: 'system',
                    content: 'Error: no code provided. Write code in codeblock in your response. ``` // example ```'
                });
                console.warn('No code block generated. Trying again.');
                no_code_failures++;
                continue;
            }
            code = res.substring(res.indexOf('```') + 3, res.lastIndexOf('```'));
            const result = await this._stageCode(code);
            if (!result) {
                console.warn('Code generation failed. Trying again.');
                continue;
            }
            return result;
        }

        console.warn('Code generation failed after maximum attempts.');
        return null;
    }

    async execute(func) {
        let return_value = null;
        try {
            return_value = await func(this.agent);
        }
        catch (err) {
            console.error(err);
            return `Code execution failed: ${err.message}`;
        }

        let result;
        if (return_value === undefined) {
            result = 'Code executed.';
        }
        else if (typeof return_value === 'string') {
            result = return_value;
        }
        else {
            try {
                result = 'Code executed: ' + JSON.stringify(return_value);
            }
            catch (error) {
                result = 'Code executed.';
            }
        }

        return result;
    }

    async lint(src) {
        let linter = new ESLint({
            overrideConfig: {
                languageOptions: {
                    ecmaVersion: 2022,
                    sourceType: 'module',
                    globals: {
                        skills: 'readonly',
                        world: 'readonly',
                        Vec3: 'readonly',
                        log: 'readonly'
                    }
                },
                rules: {
                    'no-unused-vars': 'off',
                    'no-undef': 'error'
                }
            },
            overrideConfigFile: true,
            ignore: false
        });
        const lintResult = await linter.lintText(src);
        return lintResult[0].messages;
    }

    async _stageCode(code) {
        code = this._sanitizeCode(code);
        let src = '';
        code = code.replaceAll('console.log(', 'log(bot,');
        code = code.replaceAll('log("', 'log(bot,"');

        console.log(`Generated code: """${code}"""`);

        code = code.replaceAll(';\n', '; if(bot.interrupt_code) {log(bot, "Code interrupted.");return;}\n');
        for (let line of code.split('\n')) {
            src += `    ${line}\n`;
        }
        let src_lint_copy = this.code_lint_template.replace('/* CODE HERE */', src);
        src = this.code_template.replace('/* CODE HERE */', src);

        let filename = this.file_counter + '.js';
        this.file_counter++;

        const stagedFilePath = path.join(this.fp, filename);
        let write_result = await this._writeFilePromise(stagedFilePath, src);
        const compartment = makeCompartment({
            skills,
            log: skills.log,
            world,
            Vec3,
        });
        const mainFn = compartment.evaluate(src);

        if (write_result) {
            console.error('Error writing code execution file: ' + write_result);
            return null;
        }
        return { func: { main: mainFn }, src_lint_copy: src_lint_copy };
    }

    _sanitizeCode(code) {
        code = code.trim();
        const remove_strs = ['Javascript', 'javascript', 'js'];
        for (let r of remove_strs) {
            if (code.startsWith(r)) {
                code = code.slice(r.length);
                return code;
            }
        }
        return code;
    }

    _writeFilePromise(filename, src) {
        return new Promise((resolve, reject) => {
            writeFile(filename, src, (err) => {
                if (err) {
                    reject(err);
                } else {
                    resolve();
                }
            });
        });
    }
}