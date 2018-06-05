/* @flow */

import React from "react";

import { withRouter } from "react-router";
import { connect } from "react-redux";
import { push } from "react-router-redux";

import { GTAPApi } from "../services";
import MappingEditor from "./MappingEditor";

import QuestionPicker from "metabase/containers/QuestionPicker";
import QuestionParameterTargetWidget from "metabase/parameters/containers/QuestionParameterTargetWidget";
import Button from "metabase/components/Button";
import ActionButton from "metabase/components/ActionButton";
import ModalContent from "metabase/components/ModalContent";
import LoadingAndErrorWrapper from "metabase/components/LoadingAndErrorWrapper";
import Select, { Option } from "metabase/components/Select";
import Radio from "metabase/components/Radio";
import Icon from "metabase/components/Icon";
import Tooltip from "metabase/components/Tooltip";
import RetinaImage from "react-retina-image";

import EntityObjectLoader from "metabase/entities/containers/EntityObjectLoader";
import SavedQuestionLoader from "metabase/containers/SavedQuestionLoader";

import Dimension from "metabase-lib/lib/Dimension";
import { mbqlEq } from "metabase/lib/query/util";

import _ from "underscore";
import { jt, t } from "c-3po";

const mapStateToProps = () => ({});
const mapDispatchToProps = {
  push,
};

type GTAP = {
  table_id: ?number,
  group_id: ?number,
  card_id: ?number,
  attribute_remappings: { [attribute: string]: string },
};

type Props = {
  params: { [name: string]: string },
  push: (url: string) => void,
};
type State = {
  gtap: ?GTAP,
  attributesOptions: string[],
};

@withRouter
@connect(mapStateToProps, mapDispatchToProps)
export default class GTAPModal extends React.Component {
  props: Props;
  state: State = {
    gtap: null,
    attributesOptions: null,
    simple: true,
  };
  // $FlowFixMe: componentWillMount expected to return void
  async componentWillMount() {
    const { params } = this.props;

    GTAPApi.attributes().then(attributesOptions =>
      this.setState({ attributesOptions }),
    );

    const groupId = parseInt(params.groupId);
    const tableId = parseInt(params.tableId);
    const gtaps = await GTAPApi.list();
    let gtap = _.findWhere(gtaps, { group_id: groupId, table_id: tableId });
    if (!gtap) {
      gtap = {
        table_id: tableId,
        group_id: groupId,
        card_id: null,
        attribute_remappings: { "": null },
      };
    }
    if (Object.keys(gtap.attribute_remappings).length === 0) {
      gtap.attribute_remappings = { "": null };
    }
    this.setState({ gtap, simple: gtap.card_id == null });
  }

  close = () => {
    const { push, params } = this.props;
    push(
      `/admin/permissions/databases/${params.databaseId}/schemas/${
        params.schemaName
      }/tables`,
    );
  };

  _getCanonicalGTAP() {
    let { gtap, simple } = this.state;
    if (!gtap) {
      return null;
    }
    return {
      ...gtap,
      card_id: simple ? null : gtap.card_id,
      attribute_remappings: _.pick(
        gtap.attribute_remappings,
        (value, key) => value && key,
      ),
    };
  }

  save = async () => {
    const gtap = this._getCanonicalGTAP();
    if (!gtap) {
      throw new Error("No GTAP");
    }
    if (gtap.id != null) {
      await GTAPApi.update(gtap);
    } else {
      await GTAPApi.create(gtap);
    }
    this.close();
  };

  isValid() {
    const gtap = this._getCanonicalGTAP();
    const { simple } = this.state;
    if (!gtap) {
      return false;
    } else if (simple) {
      return Object.entries(gtap.attribute_remappings).length > 0;
    } else {
      return gtap.card_id != null;
    }
  }

  render() {
    const { params } = this.props;
    const { gtap, simple, attributesOptions } = this.state;

    const valid = this.isValid();

    const remainingAttributesOptions =
      gtap && attributesOptions
        ? attributesOptions.filter(
            attribute => !(attribute in gtap.attribute_remappings),
          )
        : null;

    const hasAttributesOptions =
      Object.keys(attributesOptions || {}).length > 0;
    const hasAttributesOptionsRemaining =
      Object.keys(attributesOptions || {}).length > 0;
    const hasValidMappings =
      Object.keys((this._getCanonicalGTAP() || {}).attribute_remappings || {})
        .length > 0;

    return (
      <div>
        <h2 className="p3">{t`Grant segmented access to this table`}</h2>
        <LoadingAndErrorWrapper loading={!gtap}>
          {() =>
            gtap && (
              <div>
                <div className="px3 pb3">
                  <div className="pb3">
                    {t`When users in this group view this table they'll see a version of it that's filtered by a column or variable that's equal to one of their user attributes`}
                  </div>
                  <h4 className="pb1">
                    {t`How do you want to filter this table for users in this group?`}
                  </h4>
                  <Radio
                    value={simple}
                    options={[
                      { name: "Filter by a column in the table", value: true },
                      {
                        name: "Use a saved question to create a custom filter",
                        value: false,
                      },
                    ]}
                    onChange={simple => this.setState({ simple })}
                    isVertical
                  />
                </div>
                {!simple && (
                  <div className="px3 pb3">
                    <div className="pb2">
                      {t`Pick a saved question that filters this table or that has variables that you want to fill with user attributes.`}
                    </div>
                    <QuestionPicker
                      value={gtap.card_id}
                      onChange={card_id =>
                        this.setState({ gtap: { ...gtap, card_id } })
                      }
                    />
                  </div>
                )}
                {gtap &&
                  attributesOptions &&
                  (hasAttributesOptions || hasValidMappings ? (
                    <div className="p3 border-top border-bottom">
                      <div className="pb2">
                        {t`You can optionally add additional filters here based on user attributes. These filters will be applied on top of any filters that are already in this saved question.`}
                      </div>
                      <AttributeMappingEditor
                        value={gtap.attribute_remappings}
                        onChange={attribute_remappings =>
                          this.setState({
                            gtap: { ...gtap, attribute_remappings },
                          })
                        }
                        simple={simple}
                        gtap={gtap}
                        attributesOptions={remainingAttributesOptions}
                      />
                    </div>
                  ) : (
                    <div className="px3">
                      <AttributeOptionsEmptyState
                        title={
                          simple
                            ? t`For this option to work, your users need to have some attributes`
                            : t`To add additional filters, your users need to have some attributes`
                        }
                      />
                    </div>
                  ))}
              </div>
            )
          }
        </LoadingAndErrorWrapper>
        <div className="p3">
          {valid && (
            <div className="pb1">
              <GTAPSummary gtap={this._getCanonicalGTAP()} />
            </div>
          )}
          <div className="flex align-center justify-end">
            <Button onClick={this.close}>{t`Cancel`}</Button>
            <ActionButton
              className="ml1"
              actionFn={this.save}
              primary
              disabled={!valid}
            >
              {t`Save`}
            </ActionButton>
          </div>
        </div>
      </div>
    );
  }
}

const AttributePicker = ({ value, onChange, attributesOptions }) => (
  <div style={{ minWidth: 200 }}>
    <Select
      value={value}
      onChange={e => onChange(e.target.value)}
      placeholder={
        attributesOptions.length === 0
          ? t`No user attributes`
          : t`Pick a user attribute`
      }
      disabled={attributesOptions.length === 0}
    >
      {attributesOptions.map(attributesOption => (
        <Option key={attributesOption} value={attributesOption}>
          {attributesOption}
        </Option>
      ))}
    </Select>
  </div>
);

const QuestionTargetPicker = ({ value, onChange, questionId }) => (
  <div style={{ minWidth: 200 }}>
    <QuestionParameterTargetWidget
      questionId={questionId}
      target={value}
      onChange={onChange}
      placeholder={t`Pick a parameter`}
    />
  </div>
);

import { serializeCardForUrl } from "metabase/lib/card";

const TableTargetPicker = ({ value, onChange, tableId }) => (
  <div style={{ minWidth: 200 }}>
    <QuestionParameterTargetWidget
      // HACK: uses QuestionLoader which only takes questionId or questionHash
      questionHash={serializeCardForUrl({
        dataset_query: {
          type: "query",
          query: { source_table: tableId },
        },
      })}
      target={value}
      onChange={onChange}
      placeholder={t`Pick a column`}
    />
  </div>
);

const SummaryRow = ({ icon, content }) => (
  <div className="flex align-center">
    <Icon className="p1" name={icon} />
    <span>{content}</span>
  </div>
);

const GTAPSummary = ({ gtap }: { gtap: GTAP }) => {
  return (
    <div>
      <div className="px1 pb2 text-uppercase text-small text-grey-4">
        Summary
      </div>
      <SummaryRow
        icon="group"
        content={jt`${<GroupName groupId={gtap.group_id} />} can view`}
      />
      <SummaryRow
        icon="table2"
        content={
          gtap.card_id
            ? jt`rows in the ${(
                <QuestionName questionId={gtap.card_id} />
              )} question`
            : jt`rows in the ${<TableName tableId={gtap.table_id} />} table`
        }
      />
      {Object.entries(gtap.attribute_remappings).map(
        ([attribute, target], index) => (
          <SummaryRow
            key={attribute}
            icon="funneloutline"
            content={
              index === 0
                ? jt`where ${(
                    <TargetName gtap={gtap} target={target} />
                  )} equals ${<span className="text-code">{attribute}</span>}`
                : jt`and ${(
                    <TargetName gtap={gtap} target={target} />
                  )} equals ${<span className="text-code">{attribute}</span>}`
            }
          />
        ),
      )}
    </div>
  );
};

const GroupName = ({ groupId }) => (
  <EntityObjectLoader
    entityType="groups"
    entityId={groupId}
    properties={["name"]}
    loadingAndErrorWrapper={false}
  >
    {({ object }) => <strong>{object && object.name}</strong>}
  </EntityObjectLoader>
);

const QuestionName = ({ questionId }) => (
  <EntityObjectLoader
    entityType="questions"
    entityId={questionId}
    properties={["name"]}
    loadingAndErrorWrapper={false}
  >
    {({ object }) => <strong>{object && object.name}</strong>}
  </EntityObjectLoader>
);

const TableName = ({ tableId }) => (
  <EntityObjectLoader
    entityType="tables"
    entityId={tableId}
    properties={["display_name"]}
    loadingAndErrorWrapper={false}
  >
    {({ object }) => <strong>{object && object.display_name}</strong>}
  </EntityObjectLoader>
);

const TargetName = ({ gtap, target }) => {
  if (Array.isArray(target)) {
    if (
      (mbqlEq(target[0], "variable") || mbqlEq(target[0], "dimension")) &&
      mbqlEq(target[1][0], "template-tag")
    ) {
      return (
        <span>
          <strong>{target[1][1]}</strong> variable
        </span>
      );
    } else if (mbqlEq(target[0], "dimension")) {
      return (
        <SavedQuestionLoader questionId={gtap.card_id}>
          {({ question }) =>
            question && (
              <span>
                <strong>
                  {Dimension.parseMBQL(target[1], question.metadata()).render()}
                </strong>{" "}
                field
              </span>
            )
          }
        </SavedQuestionLoader>
      );
    }
  }
  return <emphasis>[Unknown target]</emphasis>;
};

const AttributeOptionsEmptyState = ({ title }) => (
  <div className="flex align-center rounded bg-slate-extra-light p2">
    <RetinaImage
      src="app/assets/img/attributes_illustration.png"
      className="mr2"
    />
    <div>
      <h3 className="pb1">{title}</h3>
      <div
      >{t`You can add attributes automatically by setting up an SSO that uses SAML, or you can enter them manually by going to the People section and clicking on the … menu on the far right.`}</div>
    </div>
  </div>
);

const AttributeMappingEditor = ({
  value,
  onChange,
  simple,
  attributesOptions,
  gtap,
}) => (
  <MappingEditor
    style={{ width: "100%" }}
    value={value}
    onChange={onChange}
    keyPlaceholder={t`Pick a user attribute`}
    keyHeader={
      <div className="text-uppercase text-small text-grey-4 flex align-center">
        {t`User attribute`}
        <Tooltip
          tooltip={t`We can automatically get your users’ attributes if you’ve set up SSO, or you can add them manually from the menu in the People section of the Admin Panel.`}
        >
          <Icon className="ml1" name="infooutlined" />
        </Tooltip>
      </div>
    }
    renderKeyInput={({ value, onChange }) => (
      <AttributePicker
        value={value}
        onChange={onChange}
        attributesOptions={(value ? [value] : []).concat(attributesOptions)}
      />
    )}
    render
    valuePlaceholder={simple ? t`Pick a column` : t`Pick a parameter`}
    valueHeader={
      <div className="text-uppercase text-small text-grey-4">
        {simple ? t`Column` : t`Parameter or variable`}
      </div>
    }
    renderValueInput={({ value, onChange }) =>
      simple ? (
        <TableTargetPicker
          value={value}
          onChange={onChange}
          tableId={gtap.table_id}
        />
      ) : (
        <QuestionTargetPicker
          value={value}
          onChange={onChange}
          questionId={gtap.card_id}
        />
      )
    }
    divider={<span className="px2 text-bold">{t`equals`}</span>}
    addText={t`Add a mapping`}
    canAdd={attributesOptions.length > 0}
    canDelete={true}
    swapKeyAndValue
  />
);
