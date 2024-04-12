import { expect } from '@playwright/test';
import { test } from './fixtures';

test('test', async ({ page }) => {
    await page.goto('http://localhost:3001/');
    await expect(page.locator('#main-content-container')).toContainText('This is a demo graph, changes will not be saved until you open a local folder.');
    await page.locator('.block-content >> nth=0').click();
    await page.locator('.ls-block textarea').fill('## Demo works');
    await page.getByRole('textbox').press('Tab');
    await page.getByRole('textbox').fill('/pro');
});


test('create db graph', async ({ page }) => {
    await page.goto('http://localhost:3001/');
    await expect(page.locator('#main-content-container')).toContainText('This is a demo graph, changes will not be saved until you open a local folder.');
    await page.getByRole('button', { name: 'Toggle left sidebar' }).click();
    await page.getByTitle('Logseq demo').click();
    await page.getByText('Add new graph - DB version').click();
    await page.getByPlaceholder('your graph name').fill('DemoGraph');
    await page.getByRole('button', { name: 'Submit' }).click();

    await page.locator('#create-button').click();
    await page.getByText('New page').click();

    await page.getByPlaceholder('What are you looking for?').fill('PropTestPage');
    //await page.getByPlaceholder('What are you looking for?').press('Enter');
    await page.locator('.search-results >> text="Create page"').click();
//    await page.getByPlaceholder('Create page').click();

    await page.locator('.info-title').click();
    await page.locator('.page-info div').filter({ hasText: /^Tags:Empty$/ }).locator('.property-value-inner button').click();

    // add tag=card
    await page.locator('.property-select div').filter({ hasText: /^card$/ }).locator('button').click();

    // add tag=test-page
    await page.getByPlaceholder('Set tags').fill('test-page');
    await page.locator('.property-select div').filter({ hasText: /^\+ New option:/ }).getByRole('checkbox').click();
    await page.locator('.property-select >> text=Apply').click();

    await page.waitForTimeout(1000);

    await page.locator('.ls-new-property a').click();
    await page.getByPlaceholder('Add property').fill('topic');
    await page.locator('a').filter({ hasText: '+ New option: topic' }).click();
    await page.getByLabel('Text').click();
    await page.locator('#ls-property-91-94-editor').fill('e2e-test');
    await page.locator('#ls-property-91-94-editor').blur();


    await page.locator('a').filter({ hasText: 'Add property' }).click();
    await page.locator('a').filter({ hasText: 'public' }).click();
    await page.getByRole('checkbox').click();

    await page.locator('.block-content >> nth=0').click();
    await page.locator('.ls-block textarea').fill('Hey New Page Crated!');
    await page.locator('.ls-block textarea').press('Enter');

    /*

    await page.locator('.info-title').click();
    await page.getByText('Tags:').click();
    await page.locator('div').filter({ hasText: /^Tags:Empty$/ }).getByRole('button').click();
    await page.locator('a').filter({ hasText: /^card$/ }).click();
    await page.getByRole('button', { name: 'Apply' }).click();
    await page.getByRole('button', { name: 'Empty' }).click();
    await page.getByRole('button', { name: '😉' }).first().click();
    await page.locator('#left-sidebar .journals-nav a').click();
    await page.locator('#journals').getByRole('button').nth(2).click();
    */

    const x = await page.evaluate(() => {
        return Promise.resolve("hello");
    });
    console.log(x);

});
