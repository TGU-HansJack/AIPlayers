import { readFileSync } from 'fs';

export function stripBom(text) {
    return typeof text === 'string' && text.charCodeAt(0) === 0xFEFF ? text.slice(1) : text;
}

export function readJsonFile(filePath) {
    return JSON.parse(stripBom(readFileSync(filePath, 'utf8')));
}