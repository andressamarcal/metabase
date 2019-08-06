/* @flow weak */

import type { DatasetQuery } from "metabase/meta/types/Card";
import AtomicQuery from "metabase-lib/lib/queries/AtomicQuery";

export default class InternalQuery extends AtomicQuery {
  static isDatasetQueryType(datasetQuery: DatasetQuery): boolean {
    return datasetQuery.type === "internal";
  }
}
