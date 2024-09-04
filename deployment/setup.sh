#!/bin/bash
echo $1

packages=('kinesistoneptunewriter' 'cfncustomresource')


for package in "${packages[@]}"; do
    echo "> Packaging $package"
    mkdir -p target/source/${package} target/build
    cp -r source/${package}/* target/source/${package}/   
    pip3 install --platform manylinux2014_x86_64 --target target/source/${package} --implementation cp --python-version 3.9 --only-binary=:all: -r target/source/${package}/requirements.txt --upgrade
    cd target/source/${package}
    zip -r ../../build/${package}.zip  * 
    cd ../../../
    rm -r target/source/${package} target/source/
    aws s3 cp target/build/${package}.zip s3://$1/build/${package}.zip --profile default
    echo "> UPLOAD PERFORMED"
done

mkdir -p target/source/kinesistotimestreamwriter
cp -r source/kinesistotimestreamwriter/* target/source/kinesistotimestreamwriter/ 
cd target/source/kinesistotimestreamwriter
mvn install
aws s3 cp ./target/kinesis-to-timestream-app-0.1-SNAPSHOT.jar s3://$1/build/kinesis-to-timestream-app-0.1-SNAPSHOT.jar --profile default
cd ../../../
rm -r target/source/kinesistotimestreamwriter


cftemplates=('glue' 'cst' 'nfexisting' 'nf' 'tf' 'ac' 'base')

mkdir -p target
# for cftemplate in "${cftemplates[@]}"; do
#     echo aws cloudformation package --template-file ./deployment/templates/${cftemplate}.yaml --s3-bucket $1 --output-template-file ./target/templates/${cftemplate}.yaml
#     aws cloudformation package --template-file ./deployment/templates/${cftemplate}.yaml --s3-bucket $1 --output-template-file ./target/templates/${cftemplate}.yaml
# done

cp  -r ./deployment/templates/   ./target/templates/
aws s3 sync ./target/templates/ s3://$1/templates/

echo "To deploy the solution run the deploy the below CFN template"
echo "https://s3.amazonaws.com/$1/templates/base.yaml"
