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
  attributes: string[],
};

@withRouter
@connect(mapStateToProps, mapDispatchToProps)
export default class GTAPModal extends React.Component {
  props: Props;
  state: State = {
    gtap: null,
    attributes: [],
  };
  // $FlowFixMe: componentWillMount expected to return void
  async componentWillMount() {
    const { params } = this.props;

    GTAPApi.attributes().then(attributes => this.setState({ attributes }));

    const groupId = parseInt(params.groupId);
    const tableId = parseInt(params.tableId);
    const gtaps = await GTAPApi.list();
    let gtap = _.findWhere(gtaps, { group_id: groupId, table_id: tableId });
    if (!gtap) {
      gtap = {
        table_id: tableId,
        group_id: groupId,
        card_id: null,
        attribute_remappings: { "": "" },
      };
    }
    this.setState({ gtap });
  }

  close = () => {
    const { push, params } = this.props;
    push(
      `/admin/permissions/databases/${params.databaseId}/schemas/${
        params.schemaName
      }/tables`,
    );
  };

  save = async () => {
    const { gtap } = this.state;
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
    const { gtap } = this.state;
    return (
      // has a new or saved gtap
      gtap &&
      // has a card_id
      gtap.card_id != null &&
      // has at least one non-empty attribute_remappings
      Object.entries(gtap.attribute_remappings).filter(
        ([attribute, target]) => attribute && target,
      ).length > 0
    );
  }

  render() {
    const { params } = this.props;
    const { gtap } = this.state;

    const valid = this.isValid();

    const attributes = this.state.attributes.filter(
      attribute => !(attribute in gtap.attribute_remappings),
    );

    return (
      <ModalContent
        title={t`Which question should this table be filtered by?`}
        footer={
          <div>
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
        }
        formModal
      >
        <LoadingAndErrorWrapper loading={!gtap}>
          {() =>
            gtap && (
              <div className="flex-full pb2">
                <div className="pb2">
                  <QuestionPicker
                    value={gtap.card_id}
                    onChange={card_id =>
                      this.setState({ gtap: { ...gtap, card_id } })
                    }
                  />
                </div>
                {valid && (
                  <div className="pb2">
                    <OkMessage group={params.groupId} gtap={gtap} />
                  </div>
                )}
                {gtap && (
                  <MappingEditor
                    value={gtap.attribute_remappings}
                    onChange={attribute_remappings =>
                      this.setState({ gtap: { ...gtap, attribute_remappings } })
                    }
                    keyPlaceholder={t`Attribute`}
                    keyHeader={
                      <span className="text-uppercase text-small text-grey-4 pb2">
                        {t`User attribute`}
                      </span>
                    }
                    renderKeyInput={({ value, onChange }) => (
                      <AttributePicker
                        value={value}
                        onChange={onChange}
                        attributes={(value ? [value] : []).concat(attributes)}
                      />
                    )}
                    render
                    valuePlaceholder={t`Parameter`}
                    valueHeader={
                      <span className="text-uppercase text-small text-grey-4 pb2">
                        {t`Parameter`}
                      </span>
                    }
                    renderValueInput={({ value, onChange }) => (
                      <TargetPicker
                        value={value}
                        onChange={onChange}
                        questionId={gtap.card_id}
                      />
                    )}
                    divider={
                      <span className="px2 text-bold">{t`maps to`}</span>
                    }
                    addText={t`Add a mapping`}
                    canAdd={attributes.length > 0}
                    canDelete={
                      Object.keys(gtap.attribute_remappings).length > 1
                    }
                    swapKeyAndValue
                  />
                )}
              </div>
            )
          }
        </LoadingAndErrorWrapper>
      </ModalContent>
    );
  }
}

const AttributePicker = ({ value, onChange, attributes }) => (
  <div style={{ minWidth: 200 }}>
    <Select
      value={value}
      onChange={e => onChange(e.target.value)}
      placeholder={t`Select attribute`}
    >
      {attributes.map(attribute => (
        <Option key={attribute} value={attribute}>
          {attribute}
        </Option>
      ))}
    </Select>
  </div>
);

const TargetPicker = ({ value, onChange, questionId }) => (
  <div style={{ minWidth: 200 }}>
    <QuestionParameterTargetWidget
      questionId={questionId}
      target={value}
      onChange={onChange}
    />
  </div>
);

const OkMessage = ({ group, gtap }: { group: string, gtap: GTAP }) => {
  const [attribute, target] = Object.entries(gtap.attribute_remappings)[0];
  return (
    <span>
      {jt`Okay, when users in the ${(
        <GroupName group={group} />
      )} group look at this table, it will be filtered by the ${(
        <TargetName target={target} gtap={gtap} />
      )} in this question, with each user’s ${(
        <strong>{attribute}</strong>
      )} attribute as the filter’s value.`}
    </span>
  );
};

const GroupName = ({ group }) => (
  <EntityObjectLoader
    entityType="groups"
    entityId={group}
    properties={["name"]}
    loadingAndErrorWrapper={false}
  >
    {({ object }) => <strong>{object && object.name}</strong>}
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
