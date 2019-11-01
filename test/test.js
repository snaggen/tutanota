const Promise = require('bluebird')
const path = require("path")
const destDir = path.join(__dirname, "../build/")
const fs = Promise.Promise.promisifyAll(require("fs-extra"))
const child_process = require('child_process')
const env = require('../buildSrc/env.js')
const LaunchHtml = require('../buildSrc/LaunchHtml.js')
const SystemConfig = require('../buildSrc/SystemConfig.js')
const rollup = require("rollup")

const {outConfig, rollupDebugPlugins} = require("../buildSrc/RollupConfig")


var project = null
if (process.argv.indexOf("api") !== -1) {
	project = "api"
} else if (process.argv.indexOf("client") !== -1) {
	project = "client"
} else {
	console.error("must provide 'api' or 'client' to run the tests")
	process.exit(1)
}

let testRunner = null

function resolveTestLibsPlugin() {
	const testLibs = {
		ospec: "../node_modules/ospec/ospec.js"
	}

	return {
		name: "resolve-test-libs",
		resolveId(source) {
			return testLibs[source]
		}
	}
}

async function build() {
	const bundle = await rollup.rollup({
		input: ["client/Suite.js"],
		plugins: rollupDebugPlugins("..").concat(resolveTestLibsPlugin()),
		treeshake: false,
		preserveModules: true,
	})
	return bundle.write(Object.assign({}, outConfig, {sourcemap: "inline", dir: "../build/test/client"}))
}

(async function () {
	try {
		await build()
		await createUnitTestHtml()
		// const statusCode = await runTest()
		// process.exit(statusCode)
		process.exit(0)
	} catch (e) {
		console.error(e)
		process.exit(1)
	}
})()


function runTest() {
	if (testRunner != null) {
		console.log("> skipping test run as test are already executed")
	} else {
		return new Promise((resolve) => {
			let testRunner = child_process.fork(`../build/test/${project}/bootstrapNode.js`)
			testRunner.on('exit', (code) => {
				resolve(code)
				testRunner = null
			})
		})
	}
}

async function createUnitTestHtml(watch) {
	let localEnv = env.create(SystemConfig.devTestConfig(), null, "unit-test", "Test")
	let imports = SystemConfig.baseDevDependencies.concat([`test/client/bootstrapBrowser.js`])

	const template = "System.import('./test/client/bootstrapBrowser.js')"
	await _writeFile(`../build/test-${project}.js`, [
		`window.whitelabelCustomizations = null`,
		`window.env = ${JSON.stringify(localEnv, null, 2)}`,
		watch ? "new WebSocket('ws://localhost:8080').addEventListener('message', (e) => window.hotReload())" : "",
	].join("\n") + "\n" + template)
	console.log(template)

	const html = await LaunchHtml.renderHtml(imports, localEnv)
	await _writeFile(`../build/test-${project}.html`, html)

	fs.copyFileSync("client/bootstrapBrowser.js", "../build/test/client/bootstrapBrowser.js")
}

function _writeFile(targetFile, content) {
	return fs.mkdirsAsync(path.dirname(targetFile)).then(() => fs.writeFileAsync(targetFile, content, 'utf-8'))
}
