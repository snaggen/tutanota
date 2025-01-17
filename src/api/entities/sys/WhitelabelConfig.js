// @flow

import {create, TypeRef} from "../../common/EntityFunctions"

export const WhitelabelConfigTypeRef: TypeRef<WhitelabelConfig> = new TypeRef("sys", "WhitelabelConfig")
export const _TypeModel: TypeModel = {
	"name": "WhitelabelConfig",
	"since": 22,
	"type": "ELEMENT_TYPE",
	"id": 1127,
	"rootId": "A3N5cwAEZw",
	"versioned": false,
	"encrypted": false,
	"values": {
		"_format": {"name": "_format", "id": 1131, "since": 22, "type": "Number", "cardinality": "One", "final": false, "encrypted": false},
		"_id": {"name": "_id", "id": 1129, "since": 22, "type": "GeneratedId", "cardinality": "One", "final": true, "encrypted": false},
		"_ownerGroup": {
			"name": "_ownerGroup",
			"id": 1132,
			"since": 22,
			"type": "GeneratedId",
			"cardinality": "ZeroOrOne",
			"final": true,
			"encrypted": false
		},
		"_permissions": {
			"name": "_permissions",
			"id": 1130,
			"since": 22,
			"type": "GeneratedId",
			"cardinality": "One",
			"final": true,
			"encrypted": false
		},
		"germanLanguageCode": {
			"name": "germanLanguageCode",
			"id": 1308,
			"since": 28,
			"type": "String",
			"cardinality": "ZeroOrOne",
			"final": false,
			"encrypted": false
		},
		"imprintUrl": {
			"name": "imprintUrl",
			"id": 1425,
			"since": 37,
			"type": "String",
			"cardinality": "ZeroOrOne",
			"final": false,
			"encrypted": false
		},
		"jsonTheme": {
			"name": "jsonTheme",
			"id": 1133,
			"since": 22,
			"type": "String",
			"cardinality": "One",
			"final": false,
			"encrypted": false
		},
		"metaTags": {
			"name": "metaTags",
			"id": 1281,
			"since": 26,
			"type": "String",
			"cardinality": "One",
			"final": false,
			"encrypted": false
		},
		"privacyStatementUrl": {
			"name": "privacyStatementUrl",
			"id": 1496,
			"since": 42,
			"type": "String",
			"cardinality": "ZeroOrOne",
			"final": false,
			"encrypted": false
		}
	},
	"associations": {
		"bootstrapCustomizations": {
			"name": "bootstrapCustomizations",
			"id": 1252,
			"since": 24,
			"type": "AGGREGATION",
			"cardinality": "Any",
			"refType": "BootstrapFeature",
			"final": false
		},
		"certificateInfo": {
			"name": "certificateInfo",
			"id": 1506,
			"since": 44,
			"type": "AGGREGATION",
			"cardinality": "One",
			"refType": "CertificateInfo",
			"final": false
		}
	},
	"app": "sys",
	"version": "50"
}

export function createWhitelabelConfig(values?: $Shape<$Exact<WhitelabelConfig>>): WhitelabelConfig {
	return Object.assign(create(_TypeModel, WhitelabelConfigTypeRef), values)
}
