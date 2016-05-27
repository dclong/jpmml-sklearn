/*
 * Copyright (c) 2016 Villu Ruusmann
 *
 * This file is part of JPMML-SkLearn
 *
 * JPMML-SkLearn is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JPMML-SkLearn is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JPMML-SkLearn.  If not, see <http://www.gnu.org/licenses/>.
 */
package sklearn.neighbors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.dmg.pmml.CityBlock;
import org.dmg.pmml.CompareFunctionType;
import org.dmg.pmml.ComparisonMeasure;
import org.dmg.pmml.ContinuousScoringMethodType;
import org.dmg.pmml.DataType;
import org.dmg.pmml.Euclidean;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.InlineTable;
import org.dmg.pmml.InstanceField;
import org.dmg.pmml.InstanceFields;
import org.dmg.pmml.KNNInput;
import org.dmg.pmml.KNNInputs;
import org.dmg.pmml.Measure;
import org.dmg.pmml.MiningFunctionType;
import org.dmg.pmml.MiningSchema;
import org.dmg.pmml.Minkowski;
import org.dmg.pmml.NearestNeighborModel;
import org.dmg.pmml.Row;
import org.dmg.pmml.TrainingInstances;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.ValueUtil;
import org.jpmml.sklearn.ClassDictUtil;
import org.jpmml.sklearn.DOMUtil;
import org.jpmml.sklearn.MatrixUtil;
import sklearn.Regressor;

public class KNeighborsRegressor extends Regressor {

	public KNeighborsRegressor(String module, String name){
		super(module, name);
	}

	@Override
	public int getNumberOfFeatures(){
		int[] shape = getFitXShape();

		return shape[1];
	}

	@Override
	public DataType getDataType(){
		return DataType.FLOAT;
	}

	@Override
	public NearestNeighborModel encodeModel(Schema schema){
		int[] shape = getFitXShape();

		int numberOfInstances = shape[0];
		int numberOfFeatures = shape[1];

		List<String> keys = new ArrayList<>();

		InstanceFields instanceFields = new InstanceFields();

		KNNInputs knnInputs = new KNNInputs();

		FieldName targetField = schema.getTargetField();
		if(targetField != null){
			InstanceField instanceField = new InstanceField(targetField)
				.setColumn(targetField.getValue());

			instanceFields.addInstanceFields(instanceField);

			keys.add(instanceField.getColumn());
		}

		List<FieldName> activeFields = schema.getActiveFields();
		for(FieldName activeField : activeFields){
			InstanceField instanceField = new InstanceField(activeField)
				.setColumn(activeField.getValue());

			instanceFields.addInstanceFields(instanceField);

			keys.add(instanceField.getColumn());

			KNNInput knnInput = new KNNInput(activeField);

			knnInputs.addKNNInputs(knnInput);
		}

		DocumentBuilder documentBuilder = DOMUtil.createDocumentBuilder();

		InlineTable inlineTable = new InlineTable();

		List<? extends Number> y = getY();
		if(y.size() != numberOfInstances){
			throw new IllegalArgumentException();
		}

		List<? extends Number> fitX = getFitX();

		Function<Number, String> function = new Function<Number, String>(){

			@Override
			public String apply(Number number){
				return ValueUtil.formatValue(number);
			}
		};

		for(int i = 0; i < numberOfInstances; i++){
			Iterable<? extends Number> instance = Iterables.concat(Collections.singletonList(y.get(i)), MatrixUtil.getRow(fitX, numberOfInstances, numberOfFeatures, i));

			List<String> values = Lists.newArrayList(Iterables.transform(instance, function));

			Row row = DOMUtil.createRow(documentBuilder, keys, values);

			inlineTable.addRows(row);
		}

		TrainingInstances trainingInstances = new TrainingInstances(instanceFields)
			.setInlineTable(inlineTable)
			.setTransformed(true);

		ComparisonMeasure comparisonMeasure = encodeComparisonMeasure(getMetric(), getP());

		int numberOfNeighbors = getNumberOfNeighbors();

		String weights = getWeights();
		if(!(weights).equals("uniform")){
			throw new IllegalArgumentException(weights);
		}

		MiningSchema miningSchema = ModelUtil.createMiningSchema(schema);

		NearestNeighborModel nearestNeighborModel = new NearestNeighborModel(MiningFunctionType.REGRESSION, numberOfNeighbors, miningSchema, trainingInstances, comparisonMeasure, knnInputs)
			.setContinuousScoringMethod(ContinuousScoringMethodType.AVERAGE);

		return nearestNeighborModel;
	}

	public int getNumberOfNeighbors(){
		return ValueUtil.asInt((Number)get("n_neighbors"));
	}

	public String getWeights(){
		return (String)get("weights");
	}

	public String getMetric(){
		return (String)get("metric");
	}

	public int getP(){
		return ValueUtil.asInt((Number)get("p"));
	}

	public List<? extends Number> getY(){
		return (List)ClassDictUtil.getArray(this, "_y");
	}

	public List<? extends Number> getFitX(){
		return (List)ClassDictUtil.getArray(this, "_fit_X");
	}

	private int[] getFitXShape(){
		return ClassDictUtil.getShape(this, "_fit_X", 2);
	}

	static
	private ComparisonMeasure encodeComparisonMeasure(String metric, int p){

		if(("minkowski").equals(metric)){
			Measure measure;

			switch(p){
				case 1:
					measure = new CityBlock();
					break;
				case 2:
					measure = new Euclidean();
					break;
				default:
					measure = new Minkowski(p);
					break;
			}

			ComparisonMeasure comparisonMeasure = new ComparisonMeasure(ComparisonMeasure.Kind.DISTANCE)
				.setCompareFunction(CompareFunctionType.ABS_DIFF)
				.setMeasure(measure);

			return comparisonMeasure;
		} else

		{
			throw new IllegalArgumentException(metric);
		}
	}
}