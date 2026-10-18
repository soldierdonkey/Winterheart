/**
 * Converts a number into its written English text representation,
 * including "and" for numbers over 100 (e.g., 107 -> "one hundred and seven").
 * 
 * Works in standard JavaScript and KubeJS script environments.
 * 
 * @param {number} num - The integer to convert (supports up to 999,999,999)
 * @returns {string} The text representation of the number
 */
function numberToText(num) {
    if (num === 0) return "zero";

    const ones = ["", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"];
    const teens = ["ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"];
    const tens = ["", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"];
    const thousands = ["", "thousand", "million"];

    let words = [];
    let chunkIndex = 0;

    // Helper to process numbers under 1000
    function processChunk(n, isLowestChunk) {
        let chunkWords = [];
        let hundred = Math.floor(n / 100);
        let remainder = n % 100;

        if (hundred > 0) {
            chunkWords.push(ones[hundred]);
            chunkWords.push("hundred");
        }

        if (remainder > 0) {
            // Add "and" if we have a hundred digit in this chunk, 
            // OR if this is the lowest chunk (under 1000) and the overall number is > 100
            if (hundred > 0 || (isLowestChunk && num > 100)) {
                chunkWords.push("and");
            }

            if (remainder < 10) {
                chunkWords.push(ones[remainder]);
            } else if (remainder < 20) {
                chunkWords.push(teens[remainder - 10]);
            } else {
                let tenDigit = Math.floor(remainder / 10);
                let oneDigit = remainder % 10;
                if (oneDigit > 0) {
                    chunkWords.push(tens[tenDigit] + "-" + ones[oneDigit]);
                } else {
                    chunkWords.push(tens[tenDigit]);
                }
            }
        }

        return chunkWords.join(" ");
    }

    // Split the number into groups of three digits (chunks)
    while (num > 0) {
        let chunk = num % 1000;
        if (chunk !== 0) {
            let isLowestChunk = (chunkIndex === 0);
            let chunkText = processChunk(chunk, isLowestChunk);
            let suffix = thousands[chunkIndex];
            
            if (suffix) {
                words.unshift(chunkText + " " + suffix);
            } else {
                words.unshift(chunkText);
            }
        }
        num = Math.floor(num / 1000);
        chunkIndex++;
    }

    return words.join(" ").trim();
}

const GENERATE_OMINOUS_MESSAGE = (death_count) => {
    let times = death_count === 1 ? "time" : "times";
    return `Fall ${numberToText(death_count)} ${times}, stand up ${numberToText(death_count + 1)}.`;
}